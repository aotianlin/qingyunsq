package com.campusforum.ai.service;

import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.ai.config.AiProviderProperties;
import com.campusforum.infra.audit.AuditContext;
import com.campusforum.infra.audit.AuditLogService;
import com.campusforum.infra.metrics.SecurityMetrics;
import com.campusforum.infra.security.CryptoException;
import com.campusforum.infra.security.PrivateNetworkValidator;
import com.campusforum.infra.security.TrustedProxyResolver;
import com.campusforum.tenant.TenantContext;
import com.campusforum.tenant.service.TenantService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多租户感知的 AI 服务委托者：根据当前 {@link TenantContext} 解析租户专属 AI 配置，
 * 把调用转发给对应的 {@link OpenAiCompatService} 实例（或在没有可用配置时回退
 * 到 {@link MockAiService}）。
 *
 * <p><b>解决的问题</b>（对应 bugfix.md 漏洞 12）：</p>
 * <ol>
 *   <li><b>每请求 new</b>：历史实现 {@code delegate()} 内每次调用都
 *       {@code new OpenAiCompatService(...)}，每次都重建 RestTemplate /
 *       SafeHttpClient，浪费连接池与握手成本；</li>
 *   <li><b>解密失败静默降级 mock</b>：历史实现把 {@link CryptoException}
 *       吞成 mock 响应，导致租户管理员看到的是"AI 工作正常但答非所问"，
 *       而不是"AI 配置异常"，无法及时修复；</li>
 *   <li><b>OpenAiCompatService 全局 Bean</b>：历史实现注入全局 {@code ai.api-key}，
 *       多租户场景下所有租户共用同一 key。已通过 {@link OpenAiCompatService} 解 Bean
 *       化解决（T7.1），本类是它<b>唯一合法的实例化入口</b>。</li>
 * </ol>
 *
 * <p><b>缓存策略</b>：</p>
 * <ul>
 *   <li>缓存键 = {@code tenantId}；</li>
 *   <li>指纹 = {@code baseUrl + "|" + sha256(apiKey) + "|" + model}：apiKey 用
 *       SHA-256 摘要避免缓存键中持有明文，命中相同指纹直接复用现有客户端实例；</li>
 *   <li>{@link #evict(long)} 由 {@code TenantService.updateAiConfig}
 *       在配置变更后立即调用（T7.3），确保下一次调用使用新配置。</li>
 * </ul>
 *
 * <p><b>fail-loud 行为</b>：</p>
 * <ul>
 *   <li>解密 apiKey 抛 {@link CryptoException} → 写 {@code AI_DECRYPT_FAIL} 审计 +
 *       {@code crypto_decrypt_failed_total} 计数，向上抛
 *       {@link BusinessException#BusinessException(ErrorCode)}({@link ErrorCode#AI_SERVICE_UNAVAILABLE})；</li>
 *   <li>baseUrl 命中私网 / 本机 / 链路本地 / 云元数据 → 写
 *       {@code AI_SSRF_BLOCKED} 审计 + {@code ssrf_blocked_total{stage=validator}}
 *       计数，并降级到 mock（保留历史"防御失败时不打断业务"的策略，
 *       因为 baseUrl 是 SUPER_ADMIN 配置的，错误配置不应让租户内所有用户报错）。</li>
 * </ul>
 */
@Slf4j
@Primary
@Service
@RequiredArgsConstructor
public class TenantAwareAiService implements AiService {

    private final TenantService tenantService;
    private final MockAiService mockAiService;
    private final AuditLogService auditLogService;
    private final SecurityMetrics securityMetrics;
    private final TrustedProxyResolver trustedProxyResolver;
    private final AiProviderProperties aiProviderProperties;
    private final Environment environment;
    /**
     * 注入 request-scope 的 HTTP 请求代理：仅用于在审计写入时解析客户端
     * IP / UA。AI 调用都来自登录用户的 Servlet 请求线程，注入代理本身在
     * 构造期不会触发异常；若未来引入 {@code @Async} 调用方，应在 Servlet
     * 线程提前 {@link AuditContext#from} 快照后传入异步线程。
     */
    private final HttpServletRequest httpRequest;

    /**
     * 按 tenantId 缓存 OpenAiCompatService 实例。
     *
     * <p>使用 {@link ConcurrentHashMap}：</p>
     * <ul>
     *   <li>读多写少（绝大多数请求都是命中缓存）；</li>
     *   <li>{@link ConcurrentHashMap#compute} 提供 (key) 维度的串行化保护，
     *       防止"指纹变化时多个线程同时重建"造成短暂的双实例；</li>
     *   <li>容量随租户数线性增长——CampusForum 租户数量级在百级以下，
     *       不需要 LRU 淘汰；如果未来扩到万级再换 Caffeine。</li>
     * </ul>
     */
    private final Map<Long, AiClientHolder> clientCache = new ConcurrentHashMap<>();
    private final Map<String, AiClientHolder> providerClientCache = new ConcurrentHashMap<>();

    /**
     * 缓存项：把"指纹 + 客户端实例"绑定起来，指纹变化即触发整体替换。
     *
     * @param fingerprint 配置指纹（baseUrl|sha256(apiKey)|model）
     * @param client      具体客户端实例（不可重用 / 修改）
     */
    private record AiClientHolder(String fingerprint, OpenAiCompatService client) {}

    @Override
    public String summarize(String content) {
        return delegate().summarize(content);
    }

    @Override
    public RiskResult moderate(String content) {
        return delegate().moderate(content);
    }

    @Override
    public List<String> recommendTags(String title, String content) {
        return delegate().recommendTags(title, content);
    }

    @Override
    public String chat(List<ChatMessage> messages, String context) {
        String reply = delegate().chat(messages, context);
        if (OpenAiCompatService.isUpstreamError(reply)) {
            log.warn("Tenant AI chat failed, falling back to local assistant");
            return mockAiService.chat(messages, context);
        }
        return reply;
    }

    @Override
    public String chat(List<ChatMessage> messages, String context, String model) {
        if (model != null && !model.isBlank()) {
            AiService selected = delegate(model);
            if (selected != null) {
                String reply = selected.chat(messages, context);
                if (OpenAiCompatService.isUpstreamError(reply)) {
                    log.warn("AI provider chat failed, falling back to local assistant: model={}", model);
                    return mockAiService.chat(messages, context);
                }
                return reply;
            }
        }
        return chat(messages, context);
    }

    @Override
    public boolean checkRelevance(String theme, String content) {
        return delegate().checkRelevance(theme, content);
    }

    /**
     * 配置变更时主动让指定租户的 AI 客户端缓存失效。
     *
     * <p>调用方：{@code TenantService.updateAiConfig} 在 {@code tenantMapper.updateById}
     * 之后立即调用本方法（T7.3）。下一次 AI 调用会重新走完整解析 + 校验链路，
     * 从而把新的 baseUrl / apiKey / model 立即应用到新建的客户端实例。</p>
     *
     * <p>非阻塞：底层 {@link ConcurrentHashMap#remove} 是 lock-free 操作，
     * 不会与正在进行的 {@code delegate()} 互相阻塞——即使 evict 与 delegate
     * 并发，最差结果只是同一秒内多新建一次客户端，没有正确性问题。</p>
     *
     * @param tenantId 需要失效缓存的租户 ID
     */
    public void evict(long tenantId) {
        AiClientHolder removed = clientCache.remove(tenantId);
        providerClientCache.keySet().removeIf(key -> key.startsWith("tenant:" + tenantId + ":"));
        if (removed != null) {
            log.info("AI client cache evicted: tenantId={}", tenantId);
        }
    }

    /**
     * 解析当前租户配置并返回真正用于本次调用的 {@link AiService} 实例。
     *
     * <p>分支顺序：</p>
     * <ol>
     *   <li>无 tenantId（如 standalone 模式 / 未通过租户解析）→ 直接 mock；</li>
     *   <li>租户 AI 配置解密失败（{@link CryptoException}）→ <b>fail-loud</b>，
     *       抛 {@link BusinessException}，让 {@link com.campusforum.common.GlobalExceptionHandler}
     *       转换为统一错误码返回；</li>
     *   <li>provider != openai 或 apiKey 为空 → 走 mock；</li>
     *   <li>baseUrl 命中私网 → 写审计 + ssrf 监控埋点 + 走 mock；</li>
     *   <li>正常路径：按 (tenantId, fingerprint) 命中或重建客户端缓存。</li>
     * </ol>
     */
    private AiService delegate() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return mockAiService;
        }

        Map<String, String> config;
        try {
            config = tenantService.resolveAiCredentials(tenantId);
        } catch (CryptoException e) {
            return handleDecryptFailure(tenantId, e, null);
        }

        String provider = config.get("provider");
        String apiKey = config.get("apiKey");
        String baseUrl = config.get("baseUrl");
        String model = config.get("model");

        // 仅 openai 协议且 apiKey 已配置时才走真实上游；否则一律 mock，与历史行为保持一致
        if (!"openai".equalsIgnoreCase(provider) || apiKey == null || apiKey.isBlank()) {
            return mockAiService;
        }

        // SSRF 防御：baseUrl 不允许指向内网 / 本机 / 链路本地 / 云元数据
        try {
            PrivateNetworkValidator.requirePublic(baseUrl, true);
        } catch (IllegalArgumentException ex) {
            AuditContext ctx = AuditContext.from(httpRequest, trustedProxyResolver, null, tenantId);
            auditLogService.log(ctx, "AI_SSRF_BLOCKED", "tenant", tenantId,
                    "AI baseUrl 命中 SSRF 防御：" + ex.getMessage());
            securityMetrics.ssrfBlocked("validator");
            log.warn("Tenant AI baseUrl rejected (SSRF guard): tenantId={}, error={}",
                    tenantId, ex.getMessage());
            return mockAiService;
        }

        // 配置指纹：apiKey 走 SHA-256 摘要避免缓存键中持有明文密钥
        String fingerprint = buildFingerprint(baseUrl, apiKey, model);

        // compute() 给 (tenantId) 维度提供串行化保护：
        // - 命中且指纹一致：复用现有 holder；
        // - 未命中或指纹变化：原子地创建新 holder 并写回 map。
        AiClientHolder holder = clientCache.compute(tenantId, (k, existing) -> {
            if (existing != null && existing.fingerprint().equals(fingerprint)) {
                return existing;
            }
            log.info("Building OpenAI compat client: tenantId={}, baseUrl={}, model={}",
                    tenantId, baseUrl, model);
            return new AiClientHolder(fingerprint, new OpenAiCompatService(baseUrl, apiKey, model));
        });
        return holder.client();
    }

    private AiService delegate(String requestedModel) {
        if (!aiProviderProperties.supportedModels().containsKey(requestedModel)) {
            throw new BusinessException(40000, "不支持的 AI 模型");
        }

        AiProviderProperties.Provider provider = aiProviderProperties.resolveByModel(requestedModel);
        if (provider == null || provider.getApiKey() == null || provider.getApiKey().isBlank()) {
            return delegateTenantWithRequestedModel(requestedModel);
        }

        try {
            PrivateNetworkValidator.requirePublic(provider.getBaseUrl(), true);
        } catch (IllegalArgumentException ex) {
            Long tenantId = TenantContext.getTenantId();
            if (tenantId != null) {
                AuditContext ctx = AuditContext.from(httpRequest, trustedProxyResolver, null, tenantId);
                auditLogService.log(ctx, "AI_SSRF_BLOCKED", "tenant", tenantId,
                        "AI provider baseUrl 命中 SSRF 防护：" + ex.getMessage());
            }
            securityMetrics.ssrfBlocked("validator");
            log.warn("AI provider baseUrl rejected (SSRF guard): model={}, error={}",
                    requestedModel, ex.getMessage());
            return mockAiService;
        }

        String fingerprint = buildFingerprint(provider.getBaseUrl(), provider.getApiKey(), provider.getModel());
        AiClientHolder holder = providerClientCache.compute("provider:" + provider.getModel(), (key, existing) -> {
            if (existing != null && existing.fingerprint().equals(fingerprint)) {
                return existing;
            }
            return new AiClientHolder(fingerprint,
                    new OpenAiCompatService(provider.getBaseUrl(), provider.getApiKey(), provider.getModel()));
        });
        return holder.client();
    }

    private AiService delegateTenantWithRequestedModel(String requestedModel) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return mockAiService;
        }

        Map<String, String> config;
        try {
            config = tenantService.resolveAiCredentials(tenantId);
        } catch (CryptoException e) {
            return handleDecryptFailure(tenantId, e, requestedModel);
        }

        String provider = config.get("provider");
        String apiKey = config.get("apiKey");
        String baseUrl = config.get("baseUrl");
        if (!"openai".equalsIgnoreCase(provider) || apiKey == null || apiKey.isBlank()) {
            return mockAiService;
        }

        try {
            PrivateNetworkValidator.requirePublic(baseUrl, true);
        } catch (IllegalArgumentException ex) {
            AuditContext ctx = AuditContext.from(httpRequest, trustedProxyResolver, null, tenantId);
            auditLogService.log(ctx, "AI_SSRF_BLOCKED", "tenant", tenantId,
                    "AI baseUrl 命中 SSRF 防护：" + ex.getMessage());
            securityMetrics.ssrfBlocked("validator");
            log.warn("Tenant AI baseUrl rejected (SSRF guard): tenantId={}, error={}",
                    tenantId, ex.getMessage());
            return mockAiService;
        }

        String fingerprint = buildFingerprint(baseUrl, apiKey, requestedModel);
        AiClientHolder holder = providerClientCache.compute("tenant:" + tenantId + ":" + requestedModel, (key, existing) -> {
            if (existing != null && existing.fingerprint().equals(fingerprint)) {
                return existing;
            }
            log.info("Building tenant OpenAI compat client for requested model: tenantId={}, baseUrl={}, model={}",
                    tenantId, baseUrl, requestedModel);
            return new AiClientHolder(fingerprint, new OpenAiCompatService(baseUrl, apiKey, requestedModel));
        });
        return holder.client();
    }

    private AiService handleDecryptFailure(Long tenantId, CryptoException e, String requestedModel) {
        AuditContext ctx = AuditContext.from(httpRequest, trustedProxyResolver, null, tenantId);
        auditLogService.log(ctx, "AI_DECRYPT_FAIL", "tenant", tenantId,
                "AI apiKey 解密失败：" + e.getClass().getSimpleName());
        securityMetrics.cryptoDecryptFailed();

        if (isDevProfile()) {
            log.warn("Tenant AI apiKey decrypt failed in dev profile, falling back to provider/mock: tenantId={}",
                    tenantId);
            String fallbackModel = requestedModel;
            if (fallbackModel == null || fallbackModel.isBlank()) {
                fallbackModel = aiProviderProperties.supportedModels().containsKey("deepseek-v4-flash")
                        ? "deepseek-v4-flash"
                        : null;
            }
            if (fallbackModel != null && aiProviderProperties.supportedModels().containsKey(fallbackModel)) {
                AiProviderProperties.Provider provider = aiProviderProperties.resolveByModel(fallbackModel);
                if (provider != null && provider.getApiKey() != null && !provider.getApiKey().isBlank()) {
                    try {
                        PrivateNetworkValidator.requirePublic(provider.getBaseUrl(), true);
                        String fingerprint = buildFingerprint(provider.getBaseUrl(), provider.getApiKey(), provider.getModel());
                        AiClientHolder holder = providerClientCache.compute("provider:" + provider.getModel(), (key, existing) -> {
                            if (existing != null && existing.fingerprint().equals(fingerprint)) {
                                return existing;
                            }
                            return new AiClientHolder(fingerprint,
                                    new OpenAiCompatService(provider.getBaseUrl(), provider.getApiKey(), provider.getModel()));
                        });
                        return holder.client();
                    } catch (IllegalArgumentException ex) {
                        securityMetrics.ssrfBlocked("validator");
                        log.warn("AI provider baseUrl rejected during dev fallback: model={}, error={}",
                                fallbackModel, ex.getMessage());
                    }
                }
            }
            return mockAiService;
        }

        log.warn("Tenant AI apiKey decrypt failed, fail-loud: tenantId={}", tenantId);
        throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
    }

    private boolean isDevProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if ("dev".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 计算配置指纹。
     *
     * <p>把 apiKey 替换为其 SHA-256 摘要的目的：</p>
     * <ul>
     *   <li>避免明文 apiKey 长期驻留在 ConcurrentHashMap 的 key/value 中（虽然
     *       OpenAiCompatService 内部本来就会持有明文，但缩小内存触面更稳妥）；</li>
     *   <li>仍然保证不同 apiKey → 不同指纹 → 触发缓存重建（命中冲突概率为 2^-128）。</li>
     * </ul>
     */
    private static String buildFingerprint(String baseUrl, String apiKey, String model) {
        return baseUrl + "|" + sha256Hex(apiKey == null ? "" : apiKey) + "|" + (model == null ? "" : model);
    }

    /** 以 16 进制字符串形式返回 SHA-256 摘要；SHA-256 是 Java 标准实现，不会真的抛 NoSuchAlgorithmException。 */
    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 强制实现的算法，不可能走到这里；防御性兜底以满足 checked exception
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
