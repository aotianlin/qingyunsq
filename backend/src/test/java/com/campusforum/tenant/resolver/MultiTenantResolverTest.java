package com.campusforum.tenant.resolver;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.infra.metrics.SecurityMetrics;
import com.campusforum.tenant.TenantProperties;
import com.campusforum.tenant.audit.TenantAuditService;
import com.campusforum.tenant.cache.ActiveTenantCache;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * MultiTenantResolver 单元测试：
 * mock Sa-Token + ActiveTenantCache，覆盖 4 种解析路径与 5 种失败场景。
 */
@ExtendWith(MockitoExtension.class)
class MultiTenantResolverTest {

    private MultiTenantResolver resolver;

    @Mock
    private ActiveTenantCache cache;

    @Mock
    private HttpServletRequest request;

    @Mock
    private SaSession session;

    @Mock
    private TenantAuditService tenantAuditService;

    private MockedStatic<StpUtil> stpUtilMock;

    @BeforeEach
    void setUp() {
        TenantProperties props = new TenantProperties();
        props.setRootDomain("campusforum.com");
        props.setAllowHeaderFallback(true);
        SecurityMetrics securityMetrics = new SecurityMetrics(new SimpleMeterRegistry());
        resolver = new MultiTenantResolver(props, cache, tenantAuditService, securityMetrics);

        stpUtilMock = mockStatic(StpUtil.class);
    }

    @AfterEach
    void tearDown() {
        stpUtilMock.close();
    }

    // ===== 4 种成功解析路径 =====

    @Test
    void shouldResolveFromSaTokenSession() {
        // 已认证，Session 中有 tenantId
        stpUtilMock.when(StpUtil::isLogin).thenReturn(true);
        stpUtilMock.when(StpUtil::getSession).thenReturn(session);
        when(session.get("tenantId")).thenReturn(10L);
        when(cache.getCode(10L)).thenReturn("fudan");

        ResolutionResult result = resolver.resolve(request);

        assertThat(result.tenantId()).isEqualTo(10L);
        assertThat(result.source()).isEqualTo(ResolutionResult.Source.SA_TOKEN_SESSION);
        assertThat(result.tenantCode()).isEqualTo("fudan");
    }

    @Test
    void shouldResolveFromSubdomain() {
        // 未认证，子域名匹配
        stpUtilMock.when(StpUtil::isLogin).thenReturn(false);
        when(request.getServerName()).thenReturn("fudan.campusforum.com");
        when(cache.findIdByCode("fudan")).thenReturn(Optional.of(10L));

        ResolutionResult result = resolver.resolve(request);

        assertThat(result.tenantId()).isEqualTo(10L);
        assertThat(result.source()).isEqualTo(ResolutionResult.Source.SUBDOMAIN);
        assertThat(result.tenantCode()).isEqualTo("fudan");
    }

    @Test
    void shouldResolveFromXTenantIdHeader() {
        // 未认证，子域名不匹配，X-Tenant-Id 有效
        stpUtilMock.when(StpUtil::isLogin).thenReturn(false);
        when(request.getServerName()).thenReturn("localhost");
        when(request.getHeader("X-Tenant-Id")).thenReturn("20");
        when(cache.isActive(20L)).thenReturn(true);
        when(cache.getCode(20L)).thenReturn("tongji");

        ResolutionResult result = resolver.resolve(request);

        assertThat(result.tenantId()).isEqualTo(20L);
        assertThat(result.source()).isEqualTo(ResolutionResult.Source.HEADER);
        assertThat(result.tenantCode()).isEqualTo("tongji");
    }

    @Test
    void shouldResolveFromHeaderWhenSubdomainTenantNotFound() {
        // 未认证，子域名格式匹配但租户不存在，回退到 header
        stpUtilMock.when(StpUtil::isLogin).thenReturn(false);
        when(request.getServerName()).thenReturn("unknown.campusforum.com");
        when(cache.findIdByCode("unknown")).thenReturn(Optional.empty());
        when(request.getHeader("X-Tenant-Id")).thenReturn("30");
        when(cache.isActive(30L)).thenReturn(true);
        when(cache.getCode(30L)).thenReturn("sjtu");

        ResolutionResult result = resolver.resolve(request);

        assertThat(result.tenantId()).isEqualTo(30L);
        assertThat(result.source()).isEqualTo(ResolutionResult.Source.HEADER);
        assertThat(result.tenantCode()).isEqualTo("sjtu");
    }

    // ===== 5 种失败场景 =====

    @Test
    void shouldThrowWhenSessionMissingTenantId() {
        // 已认证但 Session 中没有 tenantId
        stpUtilMock.when(StpUtil::isLogin).thenReturn(true);
        stpUtilMock.when(StpUtil::getSession).thenReturn(session);
        when(session.get("tenantId")).thenReturn(null);

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(TenantNotResolvedException.class)
                .satisfies(ex -> assertThat(((TenantNotResolvedException) ex).getReason())
                        .isEqualTo(TenantNotResolvedException.Reason.SESSION_MISSING_TENANT));
    }

    @Test
    void shouldThrowWhenNoResolverMatched() {
        // 未认证，无子域名，无 header
        stpUtilMock.when(StpUtil::isLogin).thenReturn(false);
        when(request.getServerName()).thenReturn("localhost");
        when(request.getHeader("X-Tenant-Id")).thenReturn(null);

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(TenantNotResolvedException.class)
                .satisfies(ex -> assertThat(((TenantNotResolvedException) ex).getReason())
                        .isEqualTo(TenantNotResolvedException.Reason.NO_RESOLVER_MATCHED));
    }

    @Test
    void shouldThrowWhenHeaderTenantNotActive() {
        // 未认证，X-Tenant-Id 存在但租户不活跃
        stpUtilMock.when(StpUtil::isLogin).thenReturn(false);
        when(request.getServerName()).thenReturn("localhost");
        when(request.getHeader("X-Tenant-Id")).thenReturn("99");
        when(cache.isActive(99L)).thenReturn(false);

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(TenantNotResolvedException.class)
                .satisfies(ex -> assertThat(((TenantNotResolvedException) ex).getReason())
                        .isEqualTo(TenantNotResolvedException.Reason.NO_RESOLVER_MATCHED));
    }

    @Test
    void shouldThrowWhenHeaderValueIsNotNumeric() {
        // 未认证，X-Tenant-Id 格式非法
        stpUtilMock.when(StpUtil::isLogin).thenReturn(false);
        when(request.getServerName()).thenReturn("localhost");
        when(request.getHeader("X-Tenant-Id")).thenReturn("not-a-number");

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(TenantNotResolvedException.class)
                .satisfies(ex -> assertThat(((TenantNotResolvedException) ex).getReason())
                        .isEqualTo(TenantNotResolvedException.Reason.NO_RESOLVER_MATCHED));
    }

    @Test
    void shouldThrowWhenHeaderFallbackDisabledAndNoSubdomain() {
        // 未认证，allowHeaderFallback=false，无子域名
        TenantProperties noFallbackProps = new TenantProperties();
        noFallbackProps.setRootDomain("campusforum.com");
        noFallbackProps.setAllowHeaderFallback(false);
        SecurityMetrics securityMetrics = new SecurityMetrics(new SimpleMeterRegistry());
        MultiTenantResolver noFallbackResolver = new MultiTenantResolver(
                noFallbackProps, cache, tenantAuditService, securityMetrics);

        stpUtilMock.when(StpUtil::isLogin).thenReturn(false);
        when(request.getServerName()).thenReturn("localhost");

        assertThatThrownBy(() -> noFallbackResolver.resolve(request))
                .isInstanceOf(TenantNotResolvedException.class)
                .satisfies(ex -> assertThat(((TenantNotResolvedException) ex).getReason())
                        .isEqualTo(TenantNotResolvedException.Reason.NO_RESOLVER_MATCHED));
    }
}
