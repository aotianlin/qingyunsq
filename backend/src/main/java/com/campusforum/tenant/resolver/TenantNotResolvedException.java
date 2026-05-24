package com.campusforum.tenant.resolver;

import lombok.Getter;

/**
 * 租户解析失败时抛出的异常。
 * 由 TenantResolutionFilter 捕获并转换为 HTTP 400 响应。
 */
@Getter
public class TenantNotResolvedException extends RuntimeException {

    /** 失败原因 */
    private final Reason reason;

    public TenantNotResolvedException(Reason reason) {
        super(reason.getDescription());
        this.reason = reason;
    }

    public TenantNotResolvedException(String message) {
        super(message);
        this.reason = Reason.fromCode(message);
    }

    /**
     * 租户解析失败的原因枚举
     */
    @Getter
    public enum Reason {
        /** 已认证但 Session 中缺少 tenantId */
        SESSION_MISSING_TENANT("session_missing_tenant", "已认证用户的 Session 中缺少 tenantId，请重新登录"),
        /** 所有解析策略均未匹配 */
        NO_RESOLVER_MATCHED("no_resolver_matched", "无法从请求中识别租户，请检查子域名或 X-Tenant-Id"),
        /** 子域名对应的租户不存在或已停用 */
        SUBDOMAIN_TENANT_NOT_FOUND("subdomain_tenant_not_found", "子域名对应的租户不存在或已停用"),
        /** X-Tenant-Id 对应的租户不存在或已停用 */
        HEADER_TENANT_NOT_ACTIVE("header_tenant_not_active", "X-Tenant-Id 对应的租户不存在或已停用"),
        /**
         * Sa-Token Session 与子域名解析的 tenantId 不一致 — 视觉钓鱼防护（漏洞 25 / T6.3）。
         *
         * <p>典型场景：用户登录的是租户 A（session.tenantId = A），但当前请求的子域名指向
         * 租户 B（如攻击者诱导用户访问 https://tenant-b.campusforum.com 复用已有 token）。
         * 此时若不做校验，前端会按子域名渲染 B 校的视觉皮肤，但后端按 session 给 A 校的数据，
         * 让用户看到"我登录的是 A 但页面看起来是 B"的错位状态，存在钓鱼骗操作风险。
         * 因此在 MultiTenantResolver 中显式拒绝该请求并写入 TENANT_VIOLATION_ATTEMPT 审计。</p>
         */
        TENANT_MISMATCH("tenant_mismatch", "Sa-Token Session 与子域名解析的 tenantId 不一致，已记录违规并拒绝");

        private final String code;
        private final String description;

        Reason(String code, String description) {
            this.code = code;
            this.description = description;
        }

        public static Reason fromCode(String code) {
            for (Reason r : values()) {
                if (r.code.equals(code)) {
                    return r;
                }
            }
            return NO_RESOLVER_MATCHED;
        }
    }
}
