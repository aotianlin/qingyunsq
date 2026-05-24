package com.campusforum.infra.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 安全相关 Micrometer 指标的集中埋点组件。
 *
 * <p>
 * 背景（对应 bugfix.md 漏洞 32）：项目早期专注业务实现，关键安全事件
 * （旧 ECB 解密、SSRF 拦截、MIME 不一致、登录锁定 503、WS legacy token
 * 使用、租户越权、限流 429、强制踢下线等）没有 Counter 埋点，导致加固
 * 上线后无法量化效果，也无法识别"是否仍有客户端走 legacy 路径"。
 * </p>
 *
 * <p>
 * 设计原则：
 * <ul>
 *   <li>所有 Counter 走 Micrometer，由 actuator 的 {@code /actuator/prometheus}
 *       端点暴露给运维监控（Prometheus + Grafana）。</li>
 *   <li>方法签名以"业务事件"而非"Counter 名字"为中心，调用方无需关心
 *       指标命名约定，避免命名漂移。</li>
 *   <li>Counter 名称统一以 {@code _total} 结尾（Micrometer 会再追加
 *       {@code _total} 后缀以满足 Prometheus 命名约定，因此这里直接写
 *       为业务级名称，如 {@code crypto_decrypt_legacy} 即可被暴露为
 *       {@code crypto_decrypt_legacy_total}）。</li>
 *   <li>所有 tag value 必须有界（tenant_id 仅长整型字符串、stage / reason
 *       为枚举字符串），禁止把"用户输入 / URL 路径裸值"作为 tag，避免
 *       高基数指标击穿监控存储。</li>
 * </ul>
 * </p>
 */
@Component
public class SecurityMetrics {

    /** 全局 MeterRegistry，由 Spring Boot Actuator 自动装配。 */
    private final MeterRegistry meterRegistry;

    /**
     * 构造函数注入 MeterRegistry。
     *
     * @param meterRegistry Micrometer 全局注册表
     */
    public SecurityMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 累加旧版 ECB 解密计数（Counter：{@code crypto_decrypt_legacy_total}）。
     *
     * <p>tag 含义：</p>
     * <ul>
     *   <li>{@code tenant_id}：触发解密的租户 ID（字符串形式）。运维
     *       根据该 tag 评估各租户的历史 ECB 数据迁移完成度，连续 N 天
     *       该租户计数为 0 即视为可清理 legacy 兼容代码。</li>
     * </ul>
     *
     * @param tenantId 租户 ID
     */
    public void cryptoDecryptLegacy(long tenantId) {
        Counter.builder("crypto_decrypt_legacy")
                .description("旧版 ECB 解密次数（按租户分桶，用于评估迁移完成度）")
                .tag("tenant_id", String.valueOf(tenantId))
                .register(meterRegistry)
                .increment();
    }

    /**
     * 累加加密 / 解密失败计数（Counter：{@code crypto_decrypt_failed_total}）。
     *
     * <p>无 tag。任何 CryptoException 都应该在抛出前调用一次该方法，
     * 用于运维识别"密钥配置异常 / 历史数据格式损坏 / 攻击者投毒"等
     * 异常情况。</p>
     */
    public void cryptoDecryptFailed() {
        Counter.builder("crypto_decrypt_failed")
                .description("加密 / 解密失败次数")
                .register(meterRegistry)
                .increment();
    }

    /**
     * 累加 SSRF 拦截计数（Counter：{@code ssrf_blocked_total}）。
     *
     * <p>tag 含义：</p>
     * <ul>
     *   <li>{@code stage}：拦截发生的阶段，枚举字符串，例如
     *       {@code dns_resolve}（DNS 解析阶段命中私网）、
     *       {@code redirect}（重定向跳到私网）、
     *       {@code scheme}（非法 scheme 如 file://）等。
     *       便于区分"首次请求被拒"和"重定向后被拒"。</li>
     * </ul>
     *
     * @param stage SSRF 拦截发生的阶段标识
     */
    public void ssrfBlocked(String stage) {
        Counter.builder("ssrf_blocked")
                .description("SSRF 防护拦截次数（按阶段分桶）")
                .tag("stage", stage)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 累加文件 MIME 不一致拦截计数（Counter：{@code mime_mismatch_total}）。
     *
     * <p>tag 含义：</p>
     * <ul>
     *   <li>{@code ext}：文件声明的扩展名（小写，无 . 前缀），例如 {@code png}。</li>
     *   <li>{@code detected}：Tika 通过 magic bytes 实际检测到的 MIME 类型，
     *       例如 {@code application/x-php}。</li>
     * </ul>
     *
     * <p>注意：传入 tag 前应保证 ext / detected 是已知有界集合
     * （白名单 + 黑名单），否则会引入高基数指标。</p>
     *
     * @param ext      文件扩展名
     * @param detected 实际检测到的 MIME 类型
     */
    public void mimeMismatch(String ext, String detected) {
        Counter.builder("mime_mismatch")
                .description("上传文件 MIME 不一致或命中黑名单的拦截次数")
                .tag("ext", ext)
                .tag("detected", detected)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 累加登录被锁定时返回 503 的次数（Counter：{@code login_lockout_503_total}）。
     *
     * <p>无 tag。当账号 / IP 命中登录锁定窗口时，业务返回 503 拒绝服务，
     * 运维可基于该指标识别"撞库 / 暴力破解"是否处于活跃期。</p>
     */
    public void loginLockout503() {
        Counter.builder("login_lockout_503")
                .description("登录锁定导致返回 503 的次数")
                .register(meterRegistry)
                .increment();
    }

    /**
     * 累加 WebSocket 旧版 token 路径使用次数（Counter：{@code ws_legacy_token_used_total}）。
     *
     * <p>无 tag。该指标用于评估 ticket cutover 进度：当连续 7 天计数为 0
     * 时可强制 {@code WS_TICKET_ENFORCED=true} 关闭兼容期。</p>
     */
    public void wsLegacyTokenUsed() {
        Counter.builder("ws_legacy_token_used")
                .description("WebSocket 旧版 token query 路径使用次数")
                .register(meterRegistry)
                .increment();
    }

    /**
     * 累加跨租户违规计数（Counter：{@code tenant_violation_total}）。
     *
     * <p>tag 含义：</p>
     * <ul>
     *   <li>{@code reason}：违规类型，枚举字符串，例如
     *       {@code subdomain_session_mismatch}（子域名解析与 Sa-Token Session
     *       不一致）、{@code missing_tenant_id}（请求缺租户 ID）、
     *       {@code search_without_tenant}（搜索缺 tenant filter）等。</li>
     * </ul>
     *
     * @param reason 违规原因标识
     */
    public void tenantViolation(String reason) {
        Counter.builder("tenant_violation")
                .description("跨租户违规拦截次数（按原因分桶）")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 累加限流 429 拦截计数（Counter：{@code rate_limit_429_total}）。
     *
     * <p>tag 含义：</p>
     * <ul>
     *   <li>{@code route}：被限流的"路由模板"（如 {@code /api/v1/posts/{id}}）
     *       而非具体 URI，避免 path variable 形成高基数指标。该 tag 由
     *       RateLimitInterceptor 通过 RouteTemplateExtractor 取得。</li>
     * </ul>
     *
     * @param routeTemplate 被限流的路由模板
     */
    public void rateLimit429(String routeTemplate) {
        Counter.builder("rate_limit_429")
                .description("限流 429 拦截次数（按路由模板分桶）")
                .tag("route", routeTemplate)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 累加敏感凭证变更后强制踢下线次数（Counter：{@code session_forced_logout_total}）。
     *
     * <p>tag 含义：</p>
     * <ul>
     *   <li>{@code action}：触发踢下线的业务动作，枚举字符串，例如
     *       {@code PASSWORD_CHANGE}（修改密码）、{@code PASSWORD_RESET}
     *       （重置密码）、{@code ROLE_CHANGE}（角色变更）、{@code BAN}
     *       （封禁）等。</li>
     * </ul>
     *
     * @param action 触发踢下线的业务动作
     */
    public void sessionForcedLogout(String action) {
        Counter.builder("session_forced_logout")
                .description("敏感凭证变更后强制踢下线次数（按动作分桶）")
                .tag("action", action)
                .register(meterRegistry)
                .increment();
    }
}
