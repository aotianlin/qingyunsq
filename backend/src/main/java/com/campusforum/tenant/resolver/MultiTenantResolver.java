package com.campusforum.tenant.resolver;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.infra.metrics.SecurityMetrics;
import com.campusforum.tenant.TenantProperties;
import com.campusforum.tenant.audit.TenantAuditService;
import com.campusforum.tenant.cache.ActiveTenantCache;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

/**
 * multi 模式租户解析器。
 *
 * <h2>解析优先级语义</h2>
 * <ol>
 *   <li><b>Sa-Token Session 优先</b>（已认证）：从 Session 取 {@code tenantId} 作为
 *       权威来源；同时尝试解析子域名得到 {@code subdomainTenantId}，若两者都存在
 *       且不一致 → 视为 <b>视觉钓鱼 / 跨租户复用 token</b>，立即抛
 *       {@link TenantNotResolvedException.Reason#TENANT_MISMATCH} 并写入审计、
 *       累加 {@link SecurityMetrics#tenantViolation(String)} 计数器。
 *       <p>典型场景（对应 bugfix.md 漏洞 25 / T6.3）：用户在 A 校登录得到 token，
 *       攻击者诱导用户访问 https://tenantB.campusforum.com 复用同一 token，前端
 *       会按子域名渲染 B 校皮肤，但后端按 session 给 A 校数据 — 用户看到"我登录的是 A
 *       但页面是 B"的错位状态，存在被骗操作风险。本类通过显式拒绝该请求来阻断该链路。</p>
 *   </li>
 *   <li><b>未认证回退</b>：先尝试子域名解析，再尝试 X-Tenant-Id header
 *       （受 {@link TenantProperties#isAllowHeaderFallback()} 控制），最终都未命中
 *       即抛 {@link TenantNotResolvedException.Reason#NO_RESOLVER_MATCHED}。</li>
 * </ol>
 *
 * <p>注意：此处只校验"session 与 subdomain 不一致"这一种钓鱼路径，不与 X-Tenant-Id header
 * 做一致性比对（header 一致性由 {@code TenantBindingCheckInterceptor} 在更晚阶段处理）。</p>
 */
@Component
@ConditionalOnProperty(name = "tenant.mode", havingValue = "multi")
@RequiredArgsConstructor
public class MultiTenantResolver implements TenantResolver {

    private final TenantProperties props;
    private final ActiveTenantCache cache;
    /** 用于将"session vs subdomain 不一致"事件写入 audit_logs。 */
    private final TenantAuditService tenantAuditService;
    /** 用于累加 {@code tenant_violation_total{reason="session_subdomain_mismatch"}} 计数器。 */
    private final SecurityMetrics securityMetrics;

    @Override
    public ResolutionResult resolve(HttpServletRequest request) {
        // ===== 1. 已认证请求：Sa-Token Session 是权威来源，但要与子域名做一致性比对 =====
        if (StpUtil.isLogin()) {
            // 注意：项目使用 sa-token-redis-jackson，Long 是 final 类型不会写入 Jackson 类型标记，
            // 小数值的 tenantId 反序列化回来可能是 Integer 而非 Long，直接 (Long) 强转会抛
            // ClassCastException。这里统一按 Number 取值，与 AuthController.wsTicket /
            // TenantHandshakeInterceptor 的防御写法保持一致。
            Object rawTenantId = StpUtil.getSession().get("tenantId");
            if (!(rawTenantId instanceof Number)) {
                // 已认证但 session 丢失 tenantId（可能是 Session 序列化失败 / 历史 token），
                // 强制要求重新登录，避免下游业务在 tenantId=null 的状态下越权读数据。
                throw new TenantNotResolvedException(
                        TenantNotResolvedException.Reason.SESSION_MISSING_TENANT);
            }
            long sessionTenantId = ((Number) rawTenantId).longValue();

            // 抽取出"按子域名解析 tenantId"为独立步骤；若 host 没有命中 rootDomain 或
            // code 在缓存中找不到，subdomain 会是 null，此时不做不一致校验
            // （例如客户端直接访问根域名 / IP 访问 / API 调用都属于此分支）。
            SubdomainResolution subdomain = resolveBySubdomain(request);
            if (subdomain != null && subdomain.tenantId() != sessionTenantId) {
                // 命中漏洞 25：跨租户复用 token / 视觉钓鱼。先写审计 + 埋点再抛错，
                // 即便上游捕获异常处理失败也至少留下 audit_logs 痕迹方便事后追溯。
                long userId = StpUtil.getLoginIdAsLong();
                String detail = "session=" + sessionTenantId
                        + " subdomain=" + subdomain.tenantId();
                // 适配 main 分支扩展后的 7 参签名（uri / method / ipAddress 由调用方提取）：
                String uri = request.getRequestURI();
                String method = request.getMethod();
                String ipAddress = com.campusforum.tenant.audit.TenantAuditService.resolveClientIp(request);
                tenantAuditService.recordViolationAttempt(
                        userId, sessionTenantId,
                        uri, method, ipAddress,
                        "session_subdomain_mismatch", detail);
                securityMetrics.tenantViolation("session_subdomain_mismatch");
                throw new TenantNotResolvedException(
                        TenantNotResolvedException.Reason.TENANT_MISMATCH);
            }

            // 一致 / 未提供子域名：以 session 为准走原 SA_TOKEN_SESSION 分支。
            String code = cache.getCode(sessionTenantId);
            return new ResolutionResult(sessionTenantId,
                    ResolutionResult.Source.SA_TOKEN_SESSION, code);
        }

        // ===== 2. 未认证：先尝试子域名解析 =====
        SubdomainResolution subdomain = resolveBySubdomain(request);
        if (subdomain != null) {
            return new ResolutionResult(subdomain.tenantId(),
                    ResolutionResult.Source.SUBDOMAIN, subdomain.code());
        }

        // ===== 3. 未认证：再尝试 X-Tenant-Id header 回退 =====
        if (props.isAllowHeaderFallback()) {
            String header = request.getHeader("X-Tenant-Id");
            if (header != null) {
                try {
                    long tid = Long.parseLong(header);
                    if (cache.isActive(tid)) {
                        return new ResolutionResult(tid,
                                ResolutionResult.Source.HEADER, cache.getCode(tid));
                    }
                } catch (NumberFormatException ignored) {
                    // 非法格式忽略，继续到最终拒绝
                }
            }
        }

        // ===== 4. 全部失败：拒绝 =====
        throw new TenantNotResolvedException(
                TenantNotResolvedException.Reason.NO_RESOLVER_MATCHED);
    }

    /**
     * 仅按子域名解析当前请求的 tenantId 与 code，不抛异常。
     *
     * <p>步骤：</p>
     * <ol>
     *   <li>读取 {@link HttpServletRequest#getServerName()} 作为 host；</li>
     *   <li>判断 host 是否以 {@code "." + rootDomain} 结尾；</li>
     *   <li>截出 code 段后做 {@link String#toLowerCase(Locale)} 归一化（DNS 大小写不敏感，
     *       但 {@link ActiveTenantCache#findIdByCode(String)} 走 SQL 等值比对，需要小写化
     *       避免 {@code Tenant-A.campusforum.com} 与 {@code tenant-a.campusforum.com}
     *       被识别为不同 code）；</li>
     *   <li>从缓存查 tenantId，未命中则返回 null（由调用方决定是否走下一级回退）。</li>
     * </ol>
     *
     * @param request 当前 HTTP 请求
     * @return 命中即返回 (tenantId, 归一化后的 code)；rootDomain 未配置 / host 不匹配 /
     *         code 在缓存中找不到 → null
     */
    private SubdomainResolution resolveBySubdomain(HttpServletRequest request) {
        String rootDomain = props.getRootDomain();
        if (rootDomain == null || rootDomain.isBlank()) {
            return null;
        }
        String host = request.getServerName();
        if (host == null) {
            return null;
        }
        String suffix = "." + rootDomain;
        if (!host.endsWith(suffix)) {
            return null;
        }
        String rawCode = host.substring(0, host.length() - suffix.length());
        if (rawCode.isEmpty()) {
            return null;
        }
        // DNS 标签大小写不敏感，统一小写化避免缓存命中失败造成误报"租户不存在"。
        String code = rawCode.toLowerCase(Locale.ROOT);
        Optional<Long> tid = cache.findIdByCode(code);
        return tid.map(id -> new SubdomainResolution(id, code)).orElse(null);
    }

    /** 子域名解析中间结果：tenantId + 归一化后的 code，仅在本类内部流转。 */
    private record SubdomainResolution(long tenantId, String code) {
    }
}
