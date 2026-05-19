package com.campusforum.tenant.integration;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.admin.domain.AuditLog;
import com.campusforum.admin.mapper.AuditLogMapper;
import com.campusforum.common.ErrorCode;
import com.campusforum.tenant.TenantContext;
import com.campusforum.tenant.TenantProperties;
import com.campusforum.tenant.audit.TenantAuditService;
import com.campusforum.tenant.cache.ActiveTenantCache;
import com.campusforum.tenant.filter.TenantResolutionFilter;
import com.campusforum.tenant.interceptor.TenantBindingCheckInterceptor;
import com.campusforum.tenant.resolver.MultiTenantResolver;
import com.campusforum.tenant.resolver.TenantResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * multi 模式 HTTP 越权集成测试。
 *
 * <p>验证 TenantResolutionFilter + TenantBindingCheckInterceptor 在 multi 模式下的完整流程：
 * <ol>
 *   <li>已认证用户携带其他租户的 X-Tenant-Id → 403 + audit_logs 写入</li>
 *   <li>已认证用户不携带 X-Tenant-Id → 200（从 Session 解析租户）</li>
 *   <li>已认证用户携带匹配的 X-Tenant-Id → 200</li>
 *   <li>未认证请求无任何租户来源 → 400 TENANT_NOT_RESOLVED</li>
 * </ol>
 *
 * <p><b>Validates: Property 1, Property 6</b></p>
 */
class TenantHttpIsolationIT {

    private MockMvc mvc;
    private AuditLogMapper auditLogMapper;
    private MockedStatic<StpUtil> stpUtilMock;

    private static final long USER_A_ID = 100L;
    private static final long TENANT_A = 1L;
    private static final long TENANT_B = 2L;
    private static final String TENANT_A_CODE = "tenantA";
    private static final String TENANT_B_CODE = "tenantB";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // 构建 TenantProperties（multi 模式）
        TenantProperties props = new TenantProperties();
        props.setMode(com.campusforum.tenant.TenantMode.MULTI);
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
        when(cache.getCode(TENANT_A)).thenReturn(TENANT_A_CODE);
        when(cache.getCode(TENANT_B)).thenReturn(TENANT_B_CODE);
        when(cache.findIdByCode(TENANT_A_CODE)).thenReturn(Optional.of(TENANT_A));
        when(cache.findIdByCode(TENANT_B_CODE)).thenReturn(Optional.of(TENANT_B));

        // 构建 MultiTenantResolver
        TenantResolver resolver = new MultiTenantResolver(props, cache);

        // 构建 TenantResolutionFilter
        TenantResolutionFilter filter = new TenantResolutionFilter(resolver, OBJECT_MAPPER);

        // 构建 TenantBindingCheckInterceptor
        auditLogMapper = mock(AuditLogMapper.class);
        when(auditLogMapper.insert(any(AuditLog.class))).thenReturn(1);
        TenantAuditService auditService = new TenantAuditService(auditLogMapper);
        TenantBindingCheckInterceptor bindingCheck = new TenantBindingCheckInterceptor(auditService);

        // 组装 MockMvc：Filter + Interceptor + 测试 Controller
        mvc = MockMvcBuilders.standaloneSetup(new TestApiController(), new TestAuthController())
                .addFilters(filter)
                .addInterceptors(bindingCheck)
                .build();

        // Mock StpUtil 静态方法
        stpUtilMock = mockStatic(StpUtil.class);
    }

    @AfterEach
    void tearDown() {
        stpUtilMock.close();
        TenantContext.clear();
    }

    // ========== Test Cases ==========

    @Test
    @DisplayName("1. 已认证用户携带其他租户的 X-Tenant-Id → 403 + audit_logs 多一条")
    void rejectCrossTenantHeaderManipulation() throws Exception {
        // 模拟已认证用户 A（属于 TENANT_A）
        simulateAuthenticatedUser(USER_A_ID, TENANT_A);

        mvc.perform(get("/api/v1/users/me")
                        .header("X-Tenant-Id", String.valueOf(TENANT_B)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.TENANT_VIOLATION.getCode()));

        // 验证 audit_logs 写入：count + 1
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper, times(1)).insert(captor.capture());

        AuditLog recorded = captor.getValue();
        assertThat(recorded.getOperatorId()).isEqualTo(USER_A_ID);
        assertThat(recorded.getTenantId()).isEqualTo(TENANT_A);
        assertThat(recorded.getAction()).isEqualTo("TENANT_VIOLATION_ATTEMPT");
        assertThat(recorded.getTargetType()).isEqualTo("TENANT");
        assertThat(recorded.getDetail()).contains("header_mismatch_session");
        assertThat(recorded.getDetail()).contains(String.valueOf(TENANT_B));
        assertThat(recorded.getIpAddress()).isNotNull();
    }

    @Test
    @DisplayName("2. 已认证用户不携带 X-Tenant-Id → 200（从 Session 解析租户）")
    void allowAuthenticatedRequestWithoutHeader() throws Exception {
        // 模拟已认证用户 A（属于 TENANT_A）
        simulateAuthenticatedUser(USER_A_ID, TENANT_A);

        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(content().string("user-me"));

        // 不应有审计日志写入
        verify(auditLogMapper, never()).insert(any(AuditLog.class));
    }

    @Test
    @DisplayName("3. 已认证用户携带匹配的 X-Tenant-Id → 200")
    void allowAuthenticatedRequestWithMatchingHeader() throws Exception {
        // 模拟已认证用户 A（属于 TENANT_A）
        simulateAuthenticatedUser(USER_A_ID, TENANT_A);

        mvc.perform(get("/api/v1/users/me")
                        .header("X-Tenant-Id", String.valueOf(TENANT_A)))
                .andExpect(status().isOk())
                .andExpect(content().string("user-me"));

        // 不应有审计日志写入
        verify(auditLogMapper, never()).insert(any(AuditLog.class));
    }

    @Test
    @DisplayName("4. 未认证请求无任何租户来源 → 400 TENANT_NOT_RESOLVED")
    void rejectLoginWithUnknownTenant() throws Exception {
        // 模拟未认证状态
        stpUtilMock.when(StpUtil::isLogin).thenReturn(false);

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@x\",\"password\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.TENANT_NOT_RESOLVED.getCode()));

        // 不应有审计日志写入（未认证请求不触发绑定校验）
        verify(auditLogMapper, never()).insert(any(AuditLog.class));
    }

    // ========== Helper Methods ==========

    /**
     * 模拟已认证用户：StpUtil.isLogin() = true，Session 中包含 tenantId。
     */
    private void simulateAuthenticatedUser(long userId, long tenantId) {
        stpUtilMock.when(StpUtil::isLogin).thenReturn(true);
        stpUtilMock.when(StpUtil::getLoginIdAsLong).thenReturn(userId);

        SaSession session = mock(SaSession.class);
        when(session.get("tenantId")).thenReturn(tenantId);
        stpUtilMock.when(StpUtil::getSession).thenReturn(session);
    }

    // ========== Test Controllers ==========

    /**
     * 模拟受保护的 API 端点。
     */
    @RestController
    @RequestMapping("/api/v1/users")
    static class TestApiController {
        @GetMapping("/me")
        public String me() {
            return "user-me";
        }
    }

    /**
     * 模拟 auth 端点（未认证请求的入口）。
     */
    @RestController
    @RequestMapping("/api/v1/auth")
    static class TestAuthController {
        @PostMapping("/login")
        public String login() {
            return "login-ok";
        }
    }
}
