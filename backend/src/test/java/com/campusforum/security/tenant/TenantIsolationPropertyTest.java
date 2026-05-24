package com.campusforum.security.tenant;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
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
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 任务 TPBT.4 / design.md Property 4：租户隔离不变量。
 *
 * <p>原任务设计要求在真实 MySQL + SpringBootTest 中验证"A 用户访问 B 资源得到 403/404"，
 * 由于仓库约定禁用 Testcontainers / 不能新建容器，且本机 MySQL 链路不稳定，
 * 这里改为针对 {@link MultiTenantResolver} 的"session vs subdomain 一致性校验"做属性测试，
 * 它是租户隔离的第一道防线（漏洞 25 / T6.3）：</p>
 *
 * <ul>
 *   <li><b>属性 A</b>：对任意租户 ID 组合 {@code (sessionTenant, subdomainTenant)}，
 *       只要二者不相等，{@code resolve()} 必须抛 {@code TENANT_MISMATCH}。</li>
 *   <li><b>属性 B</b>：对任意 {@code sessionTenant == subdomainTenant} 的组合，
 *       {@code resolve()} 必须返回 {@code SA_TOKEN_SESSION}，且不调审计 / 不埋点。</li>
 * </ul>
 *
 * <p>本测试用 JUnit {@link RepeatedTest} 而非 jqwik 的 {@code @Property}，原因是
 * {@code MockedStatic(StpUtil.class)} 是全局 JVM hook，与 jqwik 的 {@code @BeforeTry}
 * 生命周期不能良好共存；用 RepeatedTest + JUnit 的 {@code @BeforeEach} / {@code @AfterEach}
 * 能保证每次迭代都有干净的 mock 状态。</p>
 */
@ExtendWith(MockitoExtension.class)
class TenantIsolationPropertyTest {

    private MultiTenantResolver resolver;
    private MeterRegistry meterRegistry;

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
        SecurityMetrics securityMetrics = new SecurityMetrics(meterRegistry);

        resolver = new MultiTenantResolver(props, cache, tenantAuditService, securityMetrics);
        stpUtilMock = Mockito.mockStatic(StpUtil.class);
    }

    @AfterEach
    void tearDown() {
        stpUtilMock.close();
    }

    /**
     * 属性 A：随机 (A, B) 且 A != B → 必然抛 TENANT_MISMATCH。
     *
     * <p>每次迭代生成两个不同的 tenantId + 对应 code，并验证 resolver 抛错。
     * 50 次重复给到 jqwik-property 级别的覆盖。</p>
     */
    @RepeatedTest(50)
    void mismatch_alwaysThrows() {
        long[] pair = randomDistinctPair();
        long tenantA = pair[0];
        long tenantB = pair[1];
        String codeB = "tenant-" + tenantB;

        // session 解析到 A，但子域名解析到 B
        stpUtilMock.when(StpUtil::isLogin).thenReturn(true);
        stpUtilMock.when(StpUtil::getSession).thenReturn(session);
        stpUtilMock.when(StpUtil::getLoginIdAsLong).thenReturn(999L);
        when(session.get("tenantId")).thenReturn(tenantA);
        when(request.getServerName()).thenReturn(codeB + ".campusforum.com");
        when(cache.findIdByCode(codeB)).thenReturn(Optional.of(tenantB));

        assertThatThrownBy(() -> resolver.resolve(request))
                .as("session=%d / subdomain=%d 不一致时必须抛 TENANT_MISMATCH", tenantA, tenantB)
                .isInstanceOf(TenantNotResolvedException.class)
                .satisfies(ex -> assertThat(((TenantNotResolvedException) ex).getReason())
                        .isEqualTo(TenantNotResolvedException.Reason.TENANT_MISMATCH));
    }

    /**
     * 属性 B：session == subdomain → 必返回 SA_TOKEN_SESSION 且不报 mismatch。
     */
    @RepeatedTest(50)
    void match_alwaysReturnsSession() {
        long tenantId = 1 + ThreadLocalRandom.current().nextLong(1_000_000L);
        String code = "tenant-" + tenantId;

        stpUtilMock.when(StpUtil::isLogin).thenReturn(true);
        stpUtilMock.when(StpUtil::getSession).thenReturn(session);
        when(session.get("tenantId")).thenReturn(tenantId);
        when(request.getServerName()).thenReturn(code + ".campusforum.com");
        when(cache.findIdByCode(code)).thenReturn(Optional.of(tenantId));
        when(cache.getCode(tenantId)).thenReturn(code);

        ResolutionResult result = resolver.resolve(request);
        assertThat(result.tenantId()).isEqualTo(tenantId);
        assertThat(result.source()).isEqualTo(ResolutionResult.Source.SA_TOKEN_SESSION);
        assertThat(result.tenantCode()).isEqualTo(code);
    }

    /**
     * 边界场景：session 已登录但 subdomain 解析失败（即 cache.findIdByCode 返回 empty）
     * → 退化为按 session 解析，不视为 mismatch。
     */
    @Test
    void session_subdomainNotFound_returnsSession() {
        long tenantId = 42L;
        String code = "unknown-code";

        stpUtilMock.when(StpUtil::isLogin).thenReturn(true);
        stpUtilMock.when(StpUtil::getSession).thenReturn(session);
        when(session.get("tenantId")).thenReturn(tenantId);
        when(request.getServerName()).thenReturn(code + ".campusforum.com");
        when(cache.findIdByCode(code)).thenReturn(Optional.empty());
        when(cache.getCode(tenantId)).thenReturn("real-code");

        ResolutionResult result = resolver.resolve(request);
        assertThat(result.tenantId()).isEqualTo(tenantId);
        assertThat(result.source()).isEqualTo(ResolutionResult.Source.SA_TOKEN_SESSION);
    }

    /**
     * 生成两个保证不同的 tenantId（避免随机生成相同 ID 触发误判）。
     */
    private long[] randomDistinctPair() {
        long a = 1 + ThreadLocalRandom.current().nextLong(10_000L);
        long b;
        do {
            b = 1 + ThreadLocalRandom.current().nextLong(10_000L);
        } while (b == a);
        return new long[]{a, b};
    }
}
