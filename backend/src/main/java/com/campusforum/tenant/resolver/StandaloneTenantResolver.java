package com.campusforum.tenant.resolver;

import com.campusforum.tenant.TenantProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * standalone 模式租户解析器。
 * 始终返回 standaloneTenantId，完全忽略 X-Tenant-Id、子域名、Sa-Token Session 中的 tenantId。
 */
@Component
@ConditionalOnProperty(name = "tenant.mode", havingValue = "standalone", matchIfMissing = true)
@RequiredArgsConstructor
public class StandaloneTenantResolver implements TenantResolver {

    private final TenantProperties props;

    @Override
    public ResolutionResult resolve(HttpServletRequest request) {
        return new ResolutionResult(props.getStandaloneTenantId(),
                ResolutionResult.Source.STANDALONE_FIXED, "default");
    }
}
