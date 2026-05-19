package com.campusforum.tenant.resolver;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 租户解析器接口。
 *
 * <p>已认证请求：从 Sa-Token Session 读取，忽略客户端自报值（不一致时由 TenantBindingCheckInterceptor 检测）</p>
 * <p>未认证请求（auth 接口、WebSocket 握手）：按模式特定规则解析</p>
 */
public interface TenantResolver {

    /**
     * 解析当前请求的租户上下文。
     *
     * @param request 当前 HTTP 请求
     * @return 解析结果，包含 tenantId、来源和 tenantCode
     * @throws TenantNotResolvedException 当无法解析租户时抛出
     */
    ResolutionResult resolve(HttpServletRequest request);
}
