package com.campusforum.infra.audit;

import com.campusforum.infra.security.TrustedProxyResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Builder;
import lombok.Value;

/**
 * 审计上下文 — 封装"是谁、属于哪个租户、从哪个 IP / UA 发起"的一组不可变快照。
 *
 * <p><b>解决的问题（对应 bugfix.md 漏洞 26）</b>：早期 {@link com.campusforum.infra.audit.AuditLogService}
 * 通过 Spring 注入 request-scope 代理获取 {@link HttpServletRequest}，在异步
 * 线程（{@code CompletableFuture.runAsync} / {@code @Async}）或定时任务里调用时
 * 会抛 {@link IllegalStateException}。本对象把所需的信息在 Servlet 线程
 * 一次性"采集 + 冻结"，再让任何后台线程拿着它写审计，不再依赖
 * ThreadLocal / RequestContextHolder。</p>
 *
 * <p>常见用法：</p>
 * <pre>{@code
 * // Servlet 线程内提前生成 ctx
 * AuditContext ctx = AuditContext.from(request, trustedProxyResolver, userId, tenantId);
 *
 * CompletableFuture.runAsync(() -> {
 *     // 异步线程内仍能写审计
 *     auditLogService.log(ctx, "PASSWORD_RESET", "user", userId, "...");
 * }, executor);
 * }</pre>
 *
 * <p>字段语义：</p>
 * <ul>
 *   <li>{@code operatorId}：执行操作的用户 ID，可空（系统级动作 / 未登录）。</li>
 *   <li>{@code tenantId}：操作所属租户 ID，可空（多租户隔离仍由 MyBatis-Plus
 *       租户拦截器在持久化层强制；这里仅做信息冗余）。</li>
 *   <li>{@code clientIp}：经 {@link TrustedProxyResolver} 解析过的客户端真实 IP；
 *       仅在请求来自可信代理时才采信 X-Forwarded-For，避免 IP 栽赃。</li>
 *   <li>{@code userAgent}：客户端 User-Agent，最长 255 字符，超长由 service 层截断。</li>
 * </ul>
 */
@Value
@Builder
public class AuditContext {

    /** 操作者用户 ID。 */
    Long operatorId;

    /** 操作所属租户 ID。 */
    Long tenantId;

    /** 客户端真实 IP（已经过可信代理校验）。 */
    String clientIp;

    /** 客户端 User-Agent。 */
    String userAgent;

    /**
     * 在 Servlet 线程内基于 {@link HttpServletRequest} 构造一份 AuditContext。
     *
     * <p>调用方应该在请求入口尽早调用本方法，把上下文"快照化"，再传给后续
     * 异步链路。该方法本身不读 ThreadLocal、不抛异常（即便 request 在异步
     * 代理状态下被解引用），适合通过参数传递。</p>
     *
     * @param req         当前 HTTP 请求；若为 null 则 IP / UA 字段为空
     * @param resolver    可信代理解析器，仅当来源命中可信代理时才采信 XFF / XRI
     * @param operatorId  操作者用户 ID，调用方负责传入（通常来自 Sa-Token Session）
     * @param tenantId    租户 ID，调用方负责传入（通常来自 TenantContext）
     * @return 不可变的 AuditContext 快照
     */
    public static AuditContext from(HttpServletRequest req,
                                    TrustedProxyResolver resolver,
                                    Long operatorId,
                                    Long tenantId) {
        String ip = null;
        String ua = null;
        try {
            if (req != null && resolver != null) {
                ip = resolver.resolve(req);
                ua = req.getHeader("User-Agent");
            }
        } catch (Exception e) {
            // 忽略：异步线程下 request 代理可能抛 IllegalStateException，此处吞掉避免污染调用方
            ip = null;
            ua = null;
        }
        return AuditContext.builder()
                .operatorId(operatorId)
                .tenantId(tenantId)
                .clientIp(ip)
                .userAgent(ua)
                .build();
    }
}
