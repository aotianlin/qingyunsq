package com.campusforum.tenant.resolver;

import com.campusforum.tenant.TenantProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * StandaloneTenantResolver 单元测试：
 * 验证 standalone 模式下忽略 header/host，恒返回 standaloneTenantId。
 */
@ExtendWith(MockitoExtension.class)
class StandaloneTenantResolverTest {

    private StandaloneTenantResolver resolver;

    @Mock
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        TenantProperties props = new TenantProperties();
        props.setStandaloneTenantId(1L);
        resolver = new StandaloneTenantResolver(props);
    }

    @Test
    void shouldAlwaysReturnStandaloneTenantId() {
        ResolutionResult result = resolver.resolve(request);

        assertThat(result.tenantId()).isEqualTo(1L);
        assertThat(result.source()).isEqualTo(ResolutionResult.Source.STANDALONE_FIXED);
        assertThat(result.tenantCode()).isEqualTo("default");
    }

    @Test
    void shouldIgnoreXTenantIdHeader() {
        // standalone 模式完全不读取 request 的任何属性
        // 即使客户端发了 X-Tenant-Id=999，结果仍然是 standaloneTenantId
        ResolutionResult result = resolver.resolve(request);

        assertThat(result.tenantId()).isEqualTo(1L);
        assertThat(result.source()).isEqualTo(ResolutionResult.Source.STANDALONE_FIXED);
        // 验证 resolver 从未调用 request 的任何方法
        verifyNoInteractions(request);
    }

    @Test
    void shouldIgnoreHostSubdomain() {
        // standalone 模式完全不读取 request 的 serverName
        ResolutionResult result = resolver.resolve(request);

        assertThat(result.tenantId()).isEqualTo(1L);
        assertThat(result.source()).isEqualTo(ResolutionResult.Source.STANDALONE_FIXED);
        verifyNoInteractions(request);
    }

    @Test
    void shouldReturnCustomStandaloneTenantId() {
        TenantProperties customProps = new TenantProperties();
        customProps.setStandaloneTenantId(42L);
        StandaloneTenantResolver customResolver = new StandaloneTenantResolver(customProps);

        ResolutionResult result = customResolver.resolve(request);

        assertThat(result.tenantId()).isEqualTo(42L);
        assertThat(result.source()).isEqualTo(ResolutionResult.Source.STANDALONE_FIXED);
        assertThat(result.tenantCode()).isEqualTo("default");
    }
}
