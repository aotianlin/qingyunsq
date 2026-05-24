package com.campusforum.security.tenant;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.admin.domain.AuditLog;
import com.campusforum.infra.metrics.SecurityMetrics;
import com.campusforum.tenant.TenantProperties;
import com.campusforum.tenant.audit.TenantAuditService;
import com.campusforum.tenant.cache.ActiveTenantCache;
import com.campusforum.tenant.resolver.MultiTenantResolver;
import com.campusforum.tenant.resolver.ResolutionResult;
import com.campusforum.tenant.resolver.TenantNotResolvedException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MultiTenantResolver 的 session vs subdomain 一致性校验单元测试（漏洞 25 / T6.3）。
 *
 * <p>覆盖以下场景：</p>
 * <ol>
 *   <li>{@link #session_subdomain_mismatch_throws()}：已认证 session.tenantId=A，子域名解析得 B
 *       → 抛 {@code TENANT_MISMATCH}，{@code tenantAuditService.recordViolationAttempt} 被调用一次，
 *       {@code SecurityMetrics.tenantViolation("session_subdomain_mismatch")} Counter +1。</li>
 *   <li>{@link #session_only_returnsSession()}：已认证 session.tenantId=A，host 不带租户子域名
 *       → 返回 SA_TOKEN_SESSION，不写审计 / 不埋点。</li>
 *   <li>{@link #session_subdomain_match_returnsSession()}：已认证 session.tenantId=A，子域名也解析到 A
 *       → 返回 SA_TOKEN_SESSION，不写审计 / 不埋点（一致即放行）。</li>
 *   <li>{@link #unauth_subdomain_resolves()}：未认证 + 子域名命中
 *       → 返回 SUBDOMAIN，不走 session 一致性分支。</li>
 *   <li>{@link #unauth_subdomain_codeUppercase_normalized()}：未认证 + 子域名包含大写字母
 *       → 解析时统一小写化后能命中缓存 → 返回 SUBDOMAIN。</li>
 * </ol>
 *
 * <p>本测试用 Mockito + {@code MockedStatic(StpUtil.class)} + mock {@link HttpServletRequest}，
 * 不启动 Spring 上下文，纯 JVM 单元测试。{@link SecurityMetrics} 走真实
 * {@link SimpleMeterRegistry} 以便对 Counter 计数做断言。</p>
 */
@ExtendWith(MockitoExtension.class)
class MultiTenantResolverMismatchTest {

    private static final long TENANT_A = 10L;
    private static final long TENANT_B = 20L;
    private static final String CODE_A = "tenant-a";
    private static final String CODE_B = "tenant-b";
    private static final long USER_ID = 999L;

    private MultiTenantResolver resolver;
    private MeterRegistry meterRegistry;
    private SecurityMetrics securityMetrics;

    @Mock
    private ActiveTenantCache cache;

    @Mock
    private TenantAuditService tenantAuditService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private SaSession session;

    private MockedStatic<StpUtil> stpUtilMock;

    @BeforeEach
    void setUp() {
        TenantProperties props = new TenantProperties();
        props.setRootDomain("campusforum.com");
        props.setAllowHeaderFallback(true);

        meterRegistry = new SimpleMeterRegistry();
        securityMetrics = new SecurityMetrics(meterRegistry);

        resolver = new MultiTenantResolver(props, cache, tenantAuditService, securityMetrics);

        stpUtilMock = mockStatic(StpUtil.class);
    }

    @AfterEach
    void tearDown() {
        stpUtilMock.close();
    }

    /**
     * 关键场景（漏洞 25）：用户已登录到 A 校（session.tenantId=A），但当前请求的子域名指向 B 校
     * → 必须抛 TENANT_MISMATCH，并完成审计 + 埋点两件事。
     */
    @Test
    @DisplayName("session_subdomain_mismatch_throws: session=A 但子域名解析到 B → 抛 TENANT_MISMATCH 且写审计 + 埋点")
    void session_subdomain_mismatch_throws() {
        // Given: 已登录到 A
        stpUtilMock.when(StpUtil::isLogin).thenReturn(true);
        stpUtilMock.when(StpUtil::getSession).thenReturn(session);
        stpUtilMock.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
        when(session.get("tenantId")).thenReturn(TENANT_A);

        // 当前请求子域名指向 B 校
        when(request.getServerName()).thenReturn(CODE_B + ".campusforum.com");
        when(cache.findIdByCode(CODE_B)).thenReturn(Optional.of(TENANT_B));

        // When + Then: 抛 TENANT_MISMATCH
        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(TenantNotResolvedException.class)
                .satisfies(ex -> assertThat(((TenantNotResolvedException) ex).getReason())
                        .isEqualTo(TenantNotResolvedException.Reason.TENANT_MISMATCH));

        // 审计日志写入一次，且参数匹配 — userId / actualTenantId / reason / detail
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);
        // main 分支扩展后 recordViolationAttempt 拆分了 request → uri / method / ipAddress 三个独立参数
        verify(tenantAuditService, times(1)).recordViolationAttempt(
                eq(USER_ID), eq(TENANT_A),
                any(), any(), any(),
                reasonCaptor.capture(), detailCaptor.capture());
        assertThat(reasonCaptor.getValue()).isEqualTo("session_subdomain_mismatch");
        assertThat(detailCaptor.getValue())
                .contains("session=" + TENANT_A)
                .contains("subdomain=" + TENANT_B);

        // SecurityMetrics Counter 被累加 1 次
        double count = meterRegistry.get("tenant_violation")
                .tag("reason", "session_subdomain_mismatch")
                .counter().count();
        assertThat(count).isEqualTo(1.0);
    }

    /**
     * 已认证但请求 host 不带租户子域名（例如 IP 直连 / 根域名访问）
     * → 不会触发一致性校验，按 session 走 SA_TOKEN_SESSION。
     */
    @Test
    @DisplayName("session_only_returnsSession: 已登录 + 无子域名 → 返回 SA_TOKEN_SESSION，不写审计")
    void session_only_returnsSession() {
        stpUtilMock.when(StpUtil::isLogin).thenReturn(true);
        stpUtilMock.when(StpUtil::getSession).thenReturn(session);
        when(session.get("tenantId")).thenReturn(TENANT_A);

        // host 不以 .campusforum.com 结尾，subdomain 解析返回 null
        when(request.getServerName()).thenReturn("api.example.com");
        when(cache.getCode(TENANT_A)).thenReturn(CODE_A);

        ResolutionResult result = resolver.resolve(request);

        assertThat(result.tenantId()).isEqualTo(TENANT_A);
        assertThat(result.source()).isEqualTo(ResolutionResult.Source.SA_TOKEN_SESSION);
        assertThat(result.tenantCode()).isEqualTo(CODE_A);

        // 不写审计 / 不埋点
        verify(tenantAuditService, never())
                .recordViolationAttempt(anyLong(), anyLong(), any(), any(), any(), any(), any());
        assertThat(meterRegistry.find("tenant_violation").counter()).isNull();
    }

    /**
     * 已认证 + 子域名解析到同一个 tenantId
     * → 视为正常请求，按 session 走 SA_TOKEN_SESSION，不报 mismatch。
     */
    @Test
    @DisplayName("session_subdomain_match_returnsSession: session=A + 子域名也是 A → 返回 SA_TOKEN_SESSION")
    void session_subdomain_match_returnsSession() {
        stpUtilMock.when(StpUtil::isLogin).thenReturn(true);
        stpUtilMock.when(StpUtil::getSession).thenReturn(session);
        when(session.get("tenantId")).thenReturn(TENANT_A);

        // 子域名也解析到 A
        when(request.getServerName()).thenReturn(CODE_A + ".campusforum.com");
        when(cache.findIdByCode(CODE_A)).thenReturn(Optional.of(TENANT_A));
        when(cache.getCode(TENANT_A)).thenReturn(CODE_A);

        ResolutionResult result = resolver.resolve(request);

        assertThat(result.tenantId()).isEqualTo(TENANT_A);
        assertThat(result.source()).isEqualTo(ResolutionResult.Source.SA_TOKEN_SESSION);
        assertThat(result.tenantCode()).isEqualTo(CODE_A);

        verify(tenantAuditService, never())
                .recordViolationAttempt(anyLong(), anyLong(), any(), any(), any(), any(), any());
        assertThat(meterRegistry.find("tenant_violation").counter()).isNull();
    }

    /**
     * 未认证 + 子域名命中 → 按 SUBDOMAIN 解析，不进入 session 一致性比对分支。
     */
    @Test
    @DisplayName("unauth_subdomain_resolves: 未登录 + 子域名命中 → 返回 SUBDOMAIN")
    void unauth_subdomain_resolves() {
        stpUtilMock.when(StpUtil::isLogin).thenReturn(false);

        when(request.getServerName()).thenReturn(CODE_A + ".campusforum.com");
        when(cache.findIdByCode(CODE_A)).thenReturn(Optional.of(TENANT_A));

        ResolutionResult result = resolver.resolve(request);

        assertThat(result.tenantId()).isEqualTo(TENANT_A);
        assertThat(result.source()).isEqualTo(ResolutionResult.Source.SUBDOMAIN);
        assertThat(result.tenantCode()).isEqualTo(CODE_A);

        verify(tenantAuditService, never())
                .recordViolationAttempt(anyLong(), anyLong(), any(), any(), any(), any(), any());
    }

    /**
     * 未认证 + host 含大写字母 → 解析阶段统一小写化后命中缓存（DNS 标签大小写不敏感）。
     * 该用例同时回归"避免 Tenant-A.campusforum.com 与 tenant-a.campusforum.com 解析到不同 code"。
     */
    @Test
    @DisplayName("unauth_subdomain_codeUppercase_normalized: 大写子域名小写化后命中 → SUBDOMAIN")
    void unauth_subdomain_codeUppercase_normalized() {
        stpUtilMock.when(StpUtil::isLogin).thenReturn(false);

        // host 中包含大写字符 — 实际 DNS 解析对大小写不敏感
        when(request.getServerName()).thenReturn("Tenant-A.campusforum.com");
        // 缓存中只存了小写形式
        when(cache.findIdByCode(CODE_A)).thenReturn(Optional.of(TENANT_A));

        ResolutionResult result = resolver.resolve(request);

        assertThat(result.tenantId()).isEqualTo(TENANT_A);
        assertThat(result.source()).isEqualTo(ResolutionResult.Source.SUBDOMAIN);
        assertThat(result.tenantCode()).isEqualTo(CODE_A);
    }
}
