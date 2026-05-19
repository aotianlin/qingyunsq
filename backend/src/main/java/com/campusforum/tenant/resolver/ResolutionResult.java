package com.campusforum.tenant.resolver;

/**
 * 租户解析结果，包含解析出的 tenantId、来源和租户 code。
 */
public record ResolutionResult(long tenantId, Source source, String tenantCode) {

    public enum Source {
        /** 已认证，权威来源：Sa-Token Session */
        SA_TOKEN_SESSION,
        /** multi 模式 auth 接口，子域名解析 */
        SUBDOMAIN,
        /** multi 模式 auth 接口，X-Tenant-Id 回退 */
        HEADER,
        /** standalone 模式固定值 */
        STANDALONE_FIXED
    }
}
