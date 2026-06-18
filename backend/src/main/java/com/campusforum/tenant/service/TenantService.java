package com.campusforum.tenant.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.ai.service.TenantAwareAiService;
import com.campusforum.common.BusinessException;
import com.campusforum.infra.security.crypto.CryptoService;
import com.campusforum.tenant.cache.ActiveTenantCache;
import com.campusforum.tenant.domain.Tenant;
import com.campusforum.tenant.mapper.TenantMapper;
import com.campusforum.user.domain.User;
import com.campusforum.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    /** 复用单例 ObjectMapper：线程安全且构造开销不低，避免每次读写 AI 配置都 new。 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** AI 配置 JSON 反序列化目标类型，复用以消除 Map.class 的 unchecked 警告。 */
    private static final TypeReference<Map<String, Object>> AI_CONFIG_TYPE =
            new TypeReference<>() {};

    /** AI API Key 加密用的 HKDF purpose 标识，与其他场景密钥分域。 */
    private static final String CRYPTO_PURPOSE_AI = "tenant-ai-key";

    /** AI 配置 JSON 中的加密版本字段；2 表示新版 AES-GCM，缺失或 1 视为旧版 ECB。 */
    private static final String ENC_VERSION_FIELD = "encVersion";

    /** 当前默认加密版本号，每次升级算法递增。 */
    private static final int CURRENT_ENC_VERSION = 2;

    private final TenantMapper tenantMapper;
    private final CryptoService cryptoService;
    /**
     * 活跃租户缓存：toggleStatus 改写 status 后必须主动 evict，
     * 否则本地 Caffeine 缓存仍持有旧记录，TenantResolutionFilter / MultiTenantResolver
     * 会在 TTL 内继续把已停用租户当作"活跃"放行（对应 bugfix.md 漏洞 19）。
     */
    private final ActiveTenantCache activeTenantCache;
    /**
     * 用于在停用租户时枚举该租户全部活跃用户并逐个 kickout，
     * 保证已经握在手里的 Sa-Token 立刻失效，不会在缓存 TTL 与 Sa-Token 总有效期内
     * 形成"租户已停用 + token 仍可用"的会话残留窗口。
     */
    private final UserMapper userMapper;
    /**
     * 多租户感知的 AI 服务（持有按租户缓存的 OpenAI 客户端）。
     *
     * <p>{@link TenantAwareAiService} 内部又依赖 {@link TenantService}（用于
     * {@code resolveAiCredentials}），如果走默认构造器注入会形成 Spring Bean
     * 循环依赖。这里通过 {@link Lazy} 让 Spring 注入一个延迟代理，仅在第一次
     * 真正调用方法（{@code updateAiConfig} 内部 evict）时才解析目标 Bean，
     * 从而打破构造期循环。</p>
     *
     * <p>用途：在 {@link #updateAiConfig(Long, String, String, String, String)}
     * 完成 DB 写入后立即调用 {@code evict(tenantId)}，确保下次 AI 调用
     * 不会再使用旧配置缓存（对应 bugfix.md 漏洞 12 修复链路 T7.3）。</p>
     */
    @Lazy
    private final TenantAwareAiService tenantAwareAiService;

    public List<Tenant> listAll(String keyword, Long cursor, int limit) {
        int size = Math.min(limit, 50);
        LambdaQueryWrapper<Tenant> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(Tenant::getCode, keyword)
                    .or().like(Tenant::getName, keyword));
        }
        if (cursor != null) {
            qw.lt(Tenant::getId, cursor);
        }
        qw.orderByDesc(Tenant::getId);
        qw.last("LIMIT " + size);
        return tenantMapper.selectList(qw);
    }

    @Transactional
    public Tenant create(String code, String name, String domain) {
        LambdaQueryWrapper<Tenant> qw = new LambdaQueryWrapper<>();
        qw.eq(Tenant::getCode, code);
        if (tenantMapper.selectCount(qw) > 0) {
            throw new BusinessException(40000, "租户编码已存在");
        }
        Tenant tenant = new Tenant();
        tenant.setCode(code);
        tenant.setName(name);
        tenant.setDomain(domain);
        tenant.setStatus(1);
        tenantMapper.insert(tenant);
        log.info("Tenant created: id={}, code={}", tenant.getId(), code);
        return tenant;
    }

    @Transactional
    public Tenant update(Long id, String name, String domain, String logoUrl, String announcement) {
        Tenant tenant = tenantMapper.selectById(id);
        if (tenant == null) {
            throw new BusinessException(40000, "租户不存在");
        }
        if (name != null && !name.isBlank()) tenant.setName(name);
        if (domain != null) tenant.setDomain(domain);
        if (logoUrl != null) tenant.setLogoUrl(logoUrl);
        if (announcement != null) tenant.setAnnouncement(announcement);
        tenantMapper.updateById(tenant);
        return tenant;
    }

    /**
     * 切换租户启用 / 停用状态。
     *
     * <p>对应 bugfix.md 漏洞 19（租户停用缓存未失效 + 已停用租户的活跃用户仍能继续访问）。
     * 关键时序：<b>先 evict 缓存，再 kickout 用户</b> —— 这样即使 kickout 过程中
     * 有用户正好发起请求，TenantResolutionFilter 也已经看到最新的 status=0
     * 并直接拒绝，不会再放行任何调用进入业务层。</p>
     *
     * <ol>
     *   <li>更新 DB 中 {@code tenants.status}（1 ↔ 0）；</li>
     *   <li>立即调用 {@link ActiveTenantCache#evict(long, String)} 让 id / code
     *       两个维度的本地缓存条目都失效，避免后续解析仍走旧数据；</li>
     *   <li>仅当切换为停用（status=0）时枚举该租户全部活跃用户并调用
     *       {@link StpUtil#kickout(Object)} 让其手中的 token 立即失效。
     *       单个用户 kickout 失败不会影响其他用户与整体流程。</li>
     * </ol>
     */
    @Transactional
    public void toggleStatus(Long id) {
        Tenant tenant = tenantMapper.selectById(id);
        if (tenant == null) {
            throw new BusinessException(40000, "租户不存在");
        }
        // 先在内存对象上算出新的状态，避免 evict 时 tenant.code 已被并发改写
        int newStatus = tenant.getStatus() != null && tenant.getStatus() == 1 ? 0 : 1;
        tenant.setStatus(newStatus);
        tenantMapper.updateById(tenant);

        // 漏洞 19 修复 step 1：状态变更后立即让缓存失效，
        // 保证后续 resolve 不会再放行该租户（无论是停用还是重新启用都需要 evict，
        // 启用场景下旧的"未命中"占位也需要清掉，让下一次解析重新加载）
        activeTenantCache.evict(id, tenant.getCode());

        // 漏洞 19 修复 step 2：仅在停用时把该租户全部活跃用户踢下线
        if (newStatus == 0) {
            kickoutTenantUsers(id);
        }
        log.info("Tenant {} status changed to {}", id, newStatus);
    }

    /**
     * 枚举指定租户的活跃用户并逐个 kickout。
     *
     * <p>查询条件：{@code status=1 AND tenant_id=?}。
     * 这里直接用显式 LambdaQueryWrapper 而不是依赖 MyBatis-Plus 的全局租户拦截器，
     * 是因为 toggleStatus 的调用方通常是 SUPER_ADMIN 跨租户操作，
     * 当前 TenantContext 不一定指向被停用的目标租户。</p>
     *
     * <p>单个 user 的 {@link StpUtil#kickout(Object)} 调用以 try-catch 包裹：
     * 任何一个用户失败（比如 Redis 暂时抖动）都不会让循环中断，确保尽力把
     * 能踢下线的全部踢掉，避免出现"前一半被踢、后一半残留"的不一致。</p>
     */
    private void kickoutTenantUsers(long tenantId) {
        List<Long> userIds = userMapper.selectList(
                        new LambdaQueryWrapper<User>()
                                .eq(User::getStatus, 1)
                                .eq(User::getTenantId, tenantId))
                .stream()
                .map(User::getId)
                .toList();
        int kickedCount = 0;
        for (Long uid : userIds) {
            try {
                StpUtil.kickout(uid);
                kickedCount++;
            } catch (Exception ex) {
                // 单个用户 kickout 失败不影响整体；保留 WARN 日志便于事后排查
                log.warn("Kickout user {} failed during tenant {} disable: {}",
                        uid, tenantId, ex.getMessage());
            }
        }
        log.info("Kicked out {} active users from tenant {} (total candidates={})",
                kickedCount, tenantId, userIds.size());
    }

    public Map<String, Object> getAiConfig(Long tenantId) {
        Tenant t = tenantMapper.selectById(tenantId);
        Map<String, Object> defaults = Map.of("provider", "mock", "baseUrl", "", "apiKey", "", "model", "");
        if (t == null || t.getAiConfig() == null) return defaults;
        try {
            Map<String, Object> cfg = OBJECT_MAPPER.readValue(t.getAiConfig(), AI_CONFIG_TYPE);
            Map<String, Object> result = new HashMap<>(defaults);
            result.putAll(cfg);
            // 不回传明文 API Key：仅指示是否已配置，避免管理员页面网络面板/日志泄漏第三方密钥
            Object stored = result.get("apiKey");
            boolean configured = stored instanceof String s && !s.isBlank();
            result.put("apiKey", "");
            result.put("apiKeyConfigured", configured);
            return result;
        } catch (JsonProcessingException e) {
            return defaults;
        }
    }

    /**
     * 仅供后端内部（AI 调用链路）使用，返回明文 baseUrl/apiKey/model。不要暴露给 controller。
     *
     * <p>解密策略：根据 {@code encVersion} 字段路由：</p>
     * <ul>
     *   <li>{@code encVersion=2}：使用新版 {@link CryptoService} 解密；</li>
     *   <li>{@code encVersion} 缺失或 {@code 1}：使用旧版 ECB 兼容解密，并异步重新加密为 v2；</li>
     * </ul>
     */
    public Map<String, String> resolveAiCredentials(Long tenantId) {
        Tenant t = tenantMapper.selectById(tenantId);
        Map<String, String> credentials = new LinkedHashMap<>();
        credentials.put("provider", "mock");
        credentials.put("baseUrl", "");
        credentials.put("apiKey", "");
        credentials.put("model", "");
        if (t == null || t.getAiConfig() == null) return credentials;
        try {
            Map<String, Object> cfg = OBJECT_MAPPER.readValue(t.getAiConfig(), AI_CONFIG_TYPE);
            for (Map.Entry<String, Object> entry : cfg.entrySet()) {
                if (entry.getValue() instanceof String s) {
                    credentials.put(entry.getKey(), s);
                }
            }
            String enc = credentials.get("apiKey");
            if (enc != null && !enc.isBlank()) {
                int version = parseEncVersion(cfg.get(ENC_VERSION_FIELD));
                String plain = decryptApiKeyByVersion(enc, version, tenantId);
                credentials.put("apiKey", plain);
                credentials.remove(ENC_VERSION_FIELD); // 内部 credentials map 不暴露版本字段
                // 旧版本：触发异步重新加密为 v2，下次解密直接走新分支
                if (version < CURRENT_ENC_VERSION) {
                    asyncReencryptApiKey(tenantId, plain);
                }
            }
        } catch (JsonProcessingException ignored) {
        }
        return credentials;
    }

    /** 根据加密版本号选择解密路径。 */
    private String decryptApiKeyByVersion(String enc, int version, Long tenantId) {
        if (version >= CURRENT_ENC_VERSION) {
            return cryptoService.decrypt(enc, CRYPTO_PURPOSE_AI);
        }
        // v1 / 缺失：旧 ECB 解密。显式传入 tenantId 让 SecurityMetrics 能按租户分桶
        // 评估迁移完成度（对应 bugfix.md 漏洞 1 + 漏洞 32）。
        long tenantTag = tenantId != null ? tenantId : 0L;
        return cryptoService.decryptLegacyEcb(enc, tenantTag);
    }

    private int parseEncVersion(Object raw) {
        if (raw instanceof Number n) return n.intValue();
        if (raw instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return 1;
    }

    /**
     * 异步将 v1 ECB 密文升级为 v2 GCM。失败仅日志告警，不影响主流程。
     * 单次升级在事务外执行，避免长事务阻塞调用方。
     */
    @Async
    public void asyncReencryptApiKey(Long tenantId, String plainApiKey) {
        try {
            Tenant t = tenantMapper.selectById(tenantId);
            if (t == null || t.getAiConfig() == null) return;
            Map<String, Object> cfg = OBJECT_MAPPER.readValue(t.getAiConfig(), AI_CONFIG_TYPE);
            cfg.put("apiKey", cryptoService.encrypt(plainApiKey, CRYPTO_PURPOSE_AI));
            cfg.put(ENC_VERSION_FIELD, CURRENT_ENC_VERSION);
            t.setAiConfig(OBJECT_MAPPER.writeValueAsString(cfg));
            tenantMapper.updateById(t);
            log.info("Tenant {} AI apiKey upgraded to encVersion={}", tenantId, CURRENT_ENC_VERSION);
        } catch (Exception e) {
            // 重加密失败不抛异常，下次解密时仍可通过旧路径兜底
            log.warn("Tenant {} AI apiKey re-encrypt failed: {}", tenantId, e.getMessage());
        }
    }

    @Transactional
    public void updateAiConfig(Long tenantId, String provider, String baseUrl, String apiKey, String model) {
        Tenant t = tenantMapper.selectById(tenantId);
        if (t == null) throw new BusinessException(40000, "租户不存在");
        // 仅当 provider 为外部远程服务时校验 baseUrl 防 SSRF；mock/ollama-local 等本地形式由该校验放行
        if (baseUrl != null && !baseUrl.isBlank()
                && provider != null && "openai".equalsIgnoreCase(provider)) {
            try {
                com.campusforum.infra.security.PrivateNetworkValidator.requirePublic(baseUrl, true);
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(40000, "AI baseUrl 不合法：" + ex.getMessage());
            }
        }

        // 在已存配置基础上做 merge，避免传 null 时清空已有字段（特别是 apiKey 留空表示"不修改"）
        // cfg 用 Object 值类型，兼容 encVersion (int) 与其他字符串字段共存
        Map<String, Object> cfg = new LinkedHashMap<>();
        if (t.getAiConfig() != null && !t.getAiConfig().isBlank()) {
            try {
                Map<String, Object> existing = OBJECT_MAPPER.readValue(t.getAiConfig(), AI_CONFIG_TYPE);
                cfg.putAll(existing);
            } catch (JsonProcessingException ignored) {}
        }
        if (provider != null) cfg.put("provider", provider);
        if (baseUrl != null) cfg.put("baseUrl", baseUrl);
        // apiKey 仅在显式提供且非空时才更新；空字符串视作"保留原值"
        if (apiKey != null && !apiKey.isBlank()) {
            cfg.put("apiKey", cryptoService.encrypt(apiKey, CRYPTO_PURPOSE_AI));
            cfg.put(ENC_VERSION_FIELD, CURRENT_ENC_VERSION);
        }
        if (model != null) cfg.put("model", model);
        try {
            t.setAiConfig(OBJECT_MAPPER.writeValueAsString(cfg));
        } catch (JsonProcessingException e) {
            throw new BusinessException(40000, "序列化 AI 配置失败");
        }
        tenantMapper.updateById(t);
        // 漏洞 12 修复（T7.3）：配置变更后立即让 AI 客户端缓存失效，
        // 否则 TenantAwareAiService 内 ConcurrentHashMap 仍持有按旧 (baseUrl|apiKey|model)
        // 指纹建好的 OpenAiCompatService，下一次调用会继续走旧上游 / 旧 key，
        // 直到下一次进程重启或指纹自然变化才会替换。这里把 evict 放在 updateById 之后，
        // 保证后续无论是同步还是异步触达 delegate() 都能命中最新指纹路径并重建客户端。
        tenantAwareAiService.evict(tenantId);
    }
}
