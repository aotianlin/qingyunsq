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
        HEADER_TENANT_NOT_ACTIVE("header_tenant_not_active", "X-Tenant-Id 对应的租户不存在或已停用");

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
