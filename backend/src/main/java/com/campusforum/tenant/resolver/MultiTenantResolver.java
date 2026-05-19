package com.campusforum.tenant.resolver;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.tenant.TenantProperties;
import com.campusforum.tenant.cache.ActiveTenantCache;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * multi 模式租户解析器。
 * 按优先级解析：Sa-Token Session → 子域名 → X-Tenant-Id header。
 * 全部失败时抛出 TenantNotResolvedException。
 */
@Component
@ConditionalOnProperty(name = "tenant.mode", havingValue = "multi")
@RequiredArgsConstructor
public class MultiTenantResolver implements TenantResolver {

    private final TenantProperties props;
    private final ActiveTenantCache cache;

    @Override
    public ResolutionResult resolve(HttpServletRequest request) {
        // 1. 已认证请求：从 Sa-Token Session 读取
        if (StpUtil.isLogin()) {
            Long tid = (Long) StpUtil.getSession().get("tenantId");
            if (tid != null) {
                String code = cache.getCode(tid);
                return new ResolutionResult(tid, ResolutionResult.Source.SA_TOKEN_SESSION, code);
            }
            // 已认证但 session 没有 tenantId：要求重新登录
            throw new TenantNotResolvedException(TenantNotResolvedException.Reason.SESSION_MISSING_TENANT);
        }

        // 2. 未认证：尝试子域名
        String host = request.getServerName();
        if (props.getRootDomain() != null && host != null
                && host.endsWith("." + props.getRootDomain())) {
            String code = host.substring(0, host.length() - props.getRootDomain().length() - 1);
            Optional<Long> tid = cache.findIdByCode(code);
            if (tid.isPresent()) {
                return new ResolutionResult(tid.get(), ResolutionResult.Source.SUBDOMAIN, code);
            }
        }

        // 3. 未认证：尝试 X-Tenant-Id 头
        if (props.isAllowHeaderFallback()) {
            String header = request.getHeader("X-Tenant-Id");
            if (header != null) {
                try {
                    long tid = Long.parseLong(header);
                    if (cache.isActive(tid)) {
                        return new ResolutionResult(tid, ResolutionResult.Source.HEADER, cache.getCode(tid));
                    }
                } catch (NumberFormatException ignored) {
                    // 非法格式忽略，继续到最终拒绝
                }
            }
        }

        // 4. 全部失败：拒绝
        throw new TenantNotResolvedException(TenantNotResolvedException.Reason.NO_RESOLVER_MATCHED);
    }
}
