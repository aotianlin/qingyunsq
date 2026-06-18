package com.campusforum.tenant.integration;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.admin.mapper.AuditLogMapper;
import com.campusforum.common.ErrorCode;
import com.campusforum.infra.metrics.SecurityMetrics;
import com.campusforum.tenant.TenantContext;
import com.campusforum.tenant.TenantMode;
import com.campusforum.tenant.TenantProperties;
import com.campusforum.tenant.audit.TenantAuditService;
import com.campusforum.tenant.cache.ActiveTenantCache;
import com.campusforum.tenant.filter.TenantResolutionFilter;
import com.campusforum.tenant.resolver.MultiTenantResolver;
import com.campusforum.tenant.resolver.TenantResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * multi 模式登录场景集成测试。
 *
 * <p>使用 standalone MockMvc + mock StpUtil + mock ActiveTenantCache 验证
 * TenantResolutionFilter 在登录（未认证）请求中的租户解析行为：
 * <ul>
 *   <li>AC-2.1：子域名解析到正确租户</li>
 *   <li>AC-2.2：X-Tenant-Id header 回退</li>
 *   <li>AC-2.3：无租户来源 → 400 TENANT_NOT_RESOLVED</li>
 *   <li>AC-2.4：跨租户 email 登录失败返回 INVALID_CREDENTIALS（由 UserServiceTest 覆盖，此处验证 filter 层不阻断合法请求）</li>
 *   <li>AC-2.5：登录成功写 tenantId 到 Session（由 UserServiceTest 覆盖）</li>
 * </ul>
 *
 * <p><b>Validates: Property 2</b></p>
 */
class MultiTenantLoginIT {

    private MockMvc mvc;
    private MockedStatic<StpUtil> stpUtilMock;

    private static final long TENANT_A = 1L;
    private static final long TENANT_B = 2L;
    private static final String TENANT_A_CODE = "tenanta";
    private static final String TENANT_B_CODE = "tenantb";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // 构建 TenantProperties（multi 模式）
        TenantProperties props = new TenantProperties();
        props.setMode(TenantMode.MULTI);
        props.setRootDomain("test.local");
        props.setAllowHeaderFallback(true);
        TenantProperties.Cache cacheProps = new TenantProperties.Cache();
        cacheProps.setMaxSize(1024);
        cacheProps.setTtl(Duration.ofSeconds(60));
        props.setCache(cacheProps);

        // Mock ActiveTenantCache
        ActiveTenantCache cache = mock(ActiveTenantCache.class);
        when(cache.isActive(TENANT_A)).thenReturn(true);
        when(cache.isActive(TENANT_B)).thenReturn(true);
        when(cache.isActive(999L)).thenReturn(false);  // 不存在的租户
        when(cache.getCode(TENANT_A)).thenReturn(TENANT_A_CODE);
        when(cache.getCode(TENANT_B)).thenReturn(TENANT_B_CODE);
        when(cache.findIdByCode(TENANT_A_CODE)).thenReturn(Optional.of(TENANT_A));
        when(cache.findIdByCode(TENANT_B_CODE)).thenReturn(Optional.of(TENANT_B));
        when(cache.findIdByCode("unknown")).thenReturn(Optional.empty());

        // 构建 MultiTenantResolver
        TenantAuditService tenantAuditService = new TenantAuditService(mock(AuditLogMapper.class));
        SecurityMetrics securityMetrics = new SecurityMetrics(new SimpleMeterRegistry());
        TenantResolver resolver = new MultiTenantResolver(props, cache, tenantAuditService, securityMetrics);

        // 构建 TenantResolutionFilter
        TenantResolutionFilter filter = new TenantResolutionFilter(resolver, OBJECT_MAPPER);

        // 组装 MockMvc：Filter + 测试 Controller
        mvc = MockMvcBuilders.standaloneSetup(new TestLoginController())
                .addFilters(filter)
                .build();

        // Mock StpUtil 静态方法 — 登录场景下用户未认证
        stpUtilMock = mockStatic(StpUtil.class);
        stpUtilMock.when(StpUtil::isLogin).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        stpUtilMock.close();
        TenantContext.clear();
    }

    // ========== AC-2.1: 子域名解析到正确租户 ==========

    @Test
    @DisplayName("AC-2.1: 子域名 tenanta.test.local 解析到 TENANT_A，请求通过 filter")
    void subdomainResolvesToCorrectTenant() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .header("Host", "tenanta.test.local")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@a.com\",\"password\":\"pass123\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("login-ok:tenanta:1"));
    }

    @Test
    @DisplayName("AC-2.1: 子域名 tenantb.test.local 解析到 TENANT_B，请求通过 filter")
    void subdomainResolvesToCorrectTenantB() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .header("Host", "tenantb.test.local")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@b.com\",\"password\":\"pass123\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("login-ok:tenantb:2"));
    }

    @Test
    @DisplayName("AC-2.1: 未知子域名 unknown.test.local 且无 X-Tenant-Id → 400")
    void unknownSubdomainWithoutHeaderFallback() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .header("Host", "unknown.test.local")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@x.com\",\"password\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.TENANT_NOT_RESOLVED.getCode()));
    }

    // ========== AC-2.2: X-Tenant-Id header 回退 ==========

    @Test
    @DisplayName("AC-2.2: 无子域名时 X-Tenant-Id header 回退成功")
    void headerFallbackWhenNoSubdomain() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .header("Host", "localhost")  // 不匹配 rootDomain，无子域名
                        .header("X-Tenant-Id", String.valueOf(TENANT_A))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@a.com\",\"password\":\"pass123\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("login-ok:tenanta:1"));
    }

    @Test
    @DisplayName("AC-2.2: X-Tenant-Id 指向不存在的租户 → 400")
    void headerFallbackWithInactiveTenant() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .header("Host", "localhost")
                        .header("X-Tenant-Id", "999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@x.com\",\"password\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.TENANT_NOT_RESOLVED.getCode()));
    }

    @Test
    @DisplayName("AC-2.2: X-Tenant-Id 格式非法 → 400")
    void headerFallbackWithInvalidFormat() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .header("Host", "localhost")
                        .header("X-Tenant-Id", "not-a-number")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@x.com\",\"password\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.TENANT_NOT_RESOLVED.getCode()));
    }

    // ========== AC-2.3: 无租户来源 → 400 TENANT_NOT_RESOLVED ==========

    @Test
    @DisplayName("AC-2.3: 无子域名、无 X-Tenant-Id → 400 TENANT_NOT_RESOLVED")
    void noTenantSource() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .header("Host", "localhost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@x.com\",\"password\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.TENANT_NOT_RESOLVED.getCode()));
    }

    @Test
    @DisplayName("AC-2.3: Host 不含 rootDomain 后缀且无 header → 400")
    void hostWithoutRootDomainSuffix() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .header("Host", "some-other-domain.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@x.com\",\"password\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.TENANT_NOT_RESOLVED.getCode()));
    }

    // ========== AC-2.4 & AC-2.5: 由 UserServiceTest 覆盖 ==========
    // AC-2.4: 所有登录失败统一返回 INVALID_CREDENTIALS — 见 UserServiceTest.shouldReturnInvalidCredentialsForAllLoginFailures
    // AC-2.5: 登录成功写 tenantId 到 Session — 见 UserServiceTest.shouldWriteTenantIdToSession
    //
    // 此处仅验证 filter 层在子域名/header 解析成功后，请求能正确到达 controller，
    // 且 TenantContext 中持有正确的 tenantId（通过 controller 返回值间接验证）。

    @Test
    @DisplayName("AC-2.4 前置: 子域名解析成功后 TenantContext 持有正确 tenantId（跨租户 email 由 UserService 拒绝）")
    void subdomainResolutionSetsTenantContext() throws Exception {
        // 用 tenanta 的子域名发起登录请求，controller 返回当前 TenantContext 的 tenantId
        // 如果 email 属于 tenantb，UserService 会因 (tenant_id, email) 查不到而返回 INVALID_CREDENTIALS
        // 此处验证 filter 层正确设置了 TenantContext
        mvc.perform(post("/api/v1/auth/login")
                        .header("Host", "tenanta.test.local")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user-of-b@x.com\",\"password\":\"correct-pwd-in-b\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("login-ok:tenanta:1"));
        // 在真实场景中，UserService.login 会查 (tenant_id=1, email='user-of-b@x.com')
        // 查不到 → 抛 INVALID_CREDENTIALS。此处 controller 是 mock，仅验证 filter 层行为。
    }

    // ========== 回归：已认证分支 session.tenantId 为 Integer 时不应抛 ClassCastException ==========

    /**
     * 回归测试：sa-token-redis-jackson 反序列化后，小数值的 session tenantId 可能是
     * {@link Integer} 而非 {@link Long}。MultiTenantResolver 早期用 {@code (Long)} 强转，
     * 在 multi 模式下每个已认证请求都会抛 ClassCastException 并穿透 filter 变成 500。
     * 修复后统一按 {@link Number} 取值，此用例锁定该行为：session 持有 Integer 时
     * 请求应正常解析到对应租户，而不是 500。
     */
    @Test
    @DisplayName("回归: 已认证 session.tenantId 为 Integer 时正常解析（不抛 CCE）")
    void authenticatedSessionWithIntegerTenantIdResolves() throws Exception {
        // 模拟已登录，且 session 中 tenantId 是 Integer（Redis-Jackson 反序列化的真实形态）
        cn.dev33.satoken.session.SaSession session = mock(cn.dev33.satoken.session.SaSession.class);
        when(session.get("tenantId")).thenReturn(Integer.valueOf((int) TENANT_A));
        stpUtilMock.when(StpUtil::isLogin).thenReturn(true);
        stpUtilMock.when(StpUtil::getSession).thenReturn(session);

        mvc.perform(post("/api/v1/auth/login")
                        .header("Host", "localhost")  // 无子域名，走 session 权威分支
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@a.com\",\"password\":\"pass123\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("login-ok:tenanta:1"));
    }

    /**
     * 回归补充：已认证但 session 完全缺失 tenantId（null）时，应返回 400 TENANT_NOT_RESOLVED
     * 而非 NPE / 500，要求用户重新登录。
     */
    @Test
    @DisplayName("回归: 已认证但 session 缺失 tenantId → 400 TENANT_NOT_RESOLVED")
    void authenticatedSessionMissingTenantIdRejected() throws Exception {
        cn.dev33.satoken.session.SaSession session = mock(cn.dev33.satoken.session.SaSession.class);
        when(session.get("tenantId")).thenReturn(null);
        stpUtilMock.when(StpUtil::isLogin).thenReturn(true);
        stpUtilMock.when(StpUtil::getSession).thenReturn(session);

        mvc.perform(post("/api/v1/auth/login")
                        .header("Host", "localhost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@a.com\",\"password\":\"pass123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.TENANT_NOT_RESOLVED.getCode()));
    }

    // ========== Test Controller ==========

    /**
     * 模拟 auth 登录端点。返回当前 TenantContext 信息以验证 filter 正确设置了租户上下文。
     */
    @RestController
    @RequestMapping("/api/v1/auth")
    static class TestLoginController {
        @PostMapping("/login")
        public String login() {
            Long tenantId = TenantContext.getTenantId();
            String tenantCode = TenantContext.getTenantCode();
            return "login-ok:" + tenantCode + ":" + tenantId;
        }
    }
}
