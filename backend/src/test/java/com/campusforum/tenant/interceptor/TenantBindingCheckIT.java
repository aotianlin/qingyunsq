package com.campusforum.tenant.interceptor;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.admin.domain.AuditLog;
import com.campusforum.admin.mapper.AuditLogMapper;
import com.campusforum.common.ErrorCode;
import com.campusforum.tenant.TenantContext;
import com.campusforum.tenant.audit.TenantAuditService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 集成测试：TenantBindingCheckInterceptor
 *
 * <p>验证已认证用户携带不匹配的 X-Tenant-Id 时返回 403 并写入审计日志。</p>
 *
 * <p><b>Validates: Property 1, Property 6</b></p>
 */
class TenantBindingCheckIT {

    private MockMvc mvc;
    private AuditLogMapper auditLogMapper;
    private MockedStatic<StpUtil> stpUtilMock;

    private static final long USER_ID = 100L;
    private static final long TENANT_A = 1L;
    private static final long TENANT_B = 2L;

    @BeforeEach
    void setUp() {
        auditLogMapper = mock(AuditLogMapper.class);
        TenantAuditService auditService = new TenantAuditService(auditLogMapper);
        TenantBindingCheckInterceptor interceptor = new TenantBindingCheckInterceptor(auditService);

        mvc = MockMvcBuilders.standaloneSetup(new TestController(), new AuthTestController())
                .addInterceptors(interceptor)
                .build();

        stpUtilMock = mockStatic(StpUtil.class);
        TenantContext.setTenantId(TENANT_A);
    }

    @AfterEach
    void tearDown() {
        stpUtilMock.close();
        TenantContext.clear();
    }

    @Test
    @DisplayName("已认证用户携带其他租户的 X-Tenant-Id → 403 + audit_logs 多一条")
    void shouldRejectMismatchedTenantHeader() throws Exception {
        stpUtilMock.when(StpUtil::isLogin).thenReturn(true);
        stpUtilMock.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
        when(auditLogMapper.insert(any(AuditLog.class))).thenReturn(1);

        mvc.perform(get("/api/v1/test/resource")
                        .header("X-Tenant-Id", String.valueOf(TENANT_B)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.TENANT_VIOLATION.getCode()));

        // 验证 audit_logs 写入
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper, times(1)).insert(captor.capture());

        AuditLog recorded = captor.getValue();
        assertThat(recorded.getOperatorId()).isEqualTo(USER_ID);
        assertThat(recorded.getTenantId()).isEqualTo(TENANT_A);
        assertThat(recorded.getAction()).isEqualTo("TENANT_VIOLATION_ATTEMPT");
        assertThat(recorded.getTargetType()).isEqualTo("TENANT");
        assertThat(recorded.getDetail()).contains("header_mismatch_session");
        assertThat(recorded.getDetail()).contains(String.valueOf(TENANT_B));
    }

    @Test
    @DisplayName("已认证用户携带非法 X-Tenant-Id → 403 + audit_logs 多一条")
    void shouldRejectInvalidTenantHeader() throws Exception {
        stpUtilMock.when(StpUtil::isLogin).thenReturn(true);
        stpUtilMock.when(StpUtil::getLoginIdAsLong).thenReturn(USER_ID);
        when(auditLogMapper.insert(any(AuditLog.class))).thenReturn(1);

        mvc.perform(get("/api/v1/test/resource")
                        .header("X-Tenant-Id", "not-a-number"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.TENANT_VIOLATION.getCode()));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper, times(1)).insert(captor.capture());

        AuditLog recorded = captor.getValue();
        assertThat(recorded.getDetail()).contains("invalid_tenant_header");
    }

    @Test
    @DisplayName("已认证用户不携带 X-Tenant-Id → 正常通过")
    void shouldAllowRequestWithoutTenantHeader() throws Exception {
        stpUtilMock.when(StpUtil::isLogin).thenReturn(true);

        mvc.perform(get("/api/v1/test/resource"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));

        verify(auditLogMapper, never()).insert(any(AuditLog.class));
    }

    @Test
    @DisplayName("已认证用户携带匹配的 X-Tenant-Id → 正常通过")
    void shouldAllowRequestWithMatchingTenantHeader() throws Exception {
        stpUtilMock.when(StpUtil::isLogin).thenReturn(true);

        mvc.perform(get("/api/v1/test/resource")
                        .header("X-Tenant-Id", String.valueOf(TENANT_A)))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));

        verify(auditLogMapper, never()).insert(any(AuditLog.class));
    }

    @Test
    @DisplayName("未登录用户携带任意 X-Tenant-Id → 正常通过（不做绑定校验）")
    void shouldSkipCheckForUnauthenticatedUser() throws Exception {
        stpUtilMock.when(StpUtil::isLogin).thenReturn(false);

        mvc.perform(get("/api/v1/test/resource")
                        .header("X-Tenant-Id", String.valueOf(TENANT_B)))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));

        verify(auditLogMapper, never()).insert(any(AuditLog.class));
    }

    @Test
    @DisplayName("auth 路径不受拦截器影响（拦截器对所有路径生效但 auth 路径在 WebMvcConfig 中排除）")
    void shouldNotInterceptAuthPaths() throws Exception {
        // 注意：standaloneSetup 不支持 excludePathPatterns，
        // 此测试验证拦截器本身对未登录用户的 pass-through 行为
        stpUtilMock.when(StpUtil::isLogin).thenReturn(false);

        mvc.perform(get("/api/v1/auth/login")
                        .header("X-Tenant-Id", String.valueOf(TENANT_B)))
                .andExpect(status().isOk())
                .andExpect(content().string("auth-ok"));

        verify(auditLogMapper, never()).insert(any(AuditLog.class));
    }

    /**
     * 测试用 Controller，模拟一个受保护的 API 端点。
     */
    @RestController
    @RequestMapping("/api/v1/test")
    static class TestController {
        @GetMapping("/resource")
        public String getResource() {
            return "ok";
        }
    }

    /**
     * 测试用 Controller，模拟 auth 路径端点。
     */
    @RestController
    @RequestMapping("/api/v1/auth")
    static class AuthTestController {
        @GetMapping("/login")
        public String login() {
            return "auth-ok";
        }
    }
}
