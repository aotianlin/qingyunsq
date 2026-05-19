package com.campusforum.tenant.integration;

import com.campusforum.tenant.TenantContext;
import com.campusforum.tenant.TenantMode;
import com.campusforum.tenant.TenantProperties;
import com.campusforum.tenant.filter.TenantResolutionFilter;
import com.campusforum.tenant.resolver.StandaloneTenantResolver;
import com.campusforum.tenant.resolver.TenantResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * standalone 模式回归测试。
 *
 * <p>验证 standalone 模式下 {@link StandaloneTenantResolver} 始终返回 standaloneTenantId（默认 1L），
 * 无论客户端发送任何 X-Tenant-Id 或 Host 子域名。</p>
 *
 * <p><b>Validates: Property 3</b></p>
 */
class StandaloneModeIT {

    private MockMvc mvc;
    private static final long STANDALONE_TENANT_ID = 1L;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // 构建 TenantProperties（standalone 模式）
        TenantProperties props = new TenantProperties();
        props.setMode(TenantMode.STANDALONE);
        props.setStandaloneTenantId(STANDALONE_TENANT_ID);

        // 构建 StandaloneTenantResolver
        TenantResolver resolver = new StandaloneTenantResolver(props);

        // 构建 TenantResolutionFilter
        TenantResolutionFilter filter = new TenantResolutionFilter(resolver, OBJECT_MAPPER);

        // 组装 MockMvc：Filter + 测试 Controller
        mvc = MockMvcBuilders.standaloneSetup(new TenantEchoController())
                .addFilters(filter)
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ========== 22.1 客户端发任意 X-Tenant-Id，验证写入 tenant_id=1 ==========

    @Test
    @DisplayName("X-Tenant-Id=999 时 TenantContext 仍为 1")
    void shouldIgnoreXTenantIdHeader_999() throws Exception {
        mvc.perform(get("/api/v1/test/tenant-id")
                        .header("X-Tenant-Id", "999"))
                .andExpect(status().isOk())
                .andExpect(content().string(String.valueOf(STANDALONE_TENANT_ID)));
    }

    @Test
    @DisplayName("X-Tenant-Id=2 时 TenantContext 仍为 1")
    void shouldIgnoreXTenantIdHeader_2() throws Exception {
        mvc.perform(get("/api/v1/test/tenant-id")
                        .header("X-Tenant-Id", "2"))
                .andExpect(status().isOk())
                .andExpect(content().string(String.valueOf(STANDALONE_TENANT_ID)));
    }

    @Test
    @DisplayName("X-Tenant-Id=0 时 TenantContext 仍为 1")
    void shouldIgnoreXTenantIdHeader_0() throws Exception {
        mvc.perform(get("/api/v1/test/tenant-id")
                        .header("X-Tenant-Id", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string(String.valueOf(STANDALONE_TENANT_ID)));
    }

    @Test
    @DisplayName("X-Tenant-Id 为非数字字符串时 TenantContext 仍为 1")
    void shouldIgnoreXTenantIdHeader_nonNumeric() throws Exception {
        mvc.perform(get("/api/v1/test/tenant-id")
                        .header("X-Tenant-Id", "invalid-tenant"))
                .andExpect(status().isOk())
                .andExpect(content().string(String.valueOf(STANDALONE_TENANT_ID)));
    }

    // ========== 客户端发任意 Host 子域名，验证写入 tenant_id=1 ==========

    @Test
    @DisplayName("Host=tenantA.test.local 时 TenantContext 仍为 1")
    void shouldIgnoreHostSubdomain_tenantA() throws Exception {
        mvc.perform(get("/api/v1/test/tenant-id")
                        .header("Host", "tenantA.test.local"))
                .andExpect(status().isOk())
                .andExpect(content().string(String.valueOf(STANDALONE_TENANT_ID)));
    }

    @Test
    @DisplayName("Host=school-b.campusforum.com 时 TenantContext 仍为 1")
    void shouldIgnoreHostSubdomain_schoolB() throws Exception {
        mvc.perform(get("/api/v1/test/tenant-id")
                        .header("Host", "school-b.campusforum.com"))
                .andExpect(status().isOk())
                .andExpect(content().string(String.valueOf(STANDALONE_TENANT_ID)));
    }

    @Test
    @DisplayName("同时携带 X-Tenant-Id 和 Host 子域名时 TenantContext 仍为 1")
    void shouldIgnoreBothHeaderAndHost() throws Exception {
        mvc.perform(get("/api/v1/test/tenant-id")
                        .header("X-Tenant-Id", "42")
                        .header("Host", "evil.attacker.com"))
                .andExpect(status().isOk())
                .andExpect(content().string(String.valueOf(STANDALONE_TENANT_ID)));
    }

    @Test
    @DisplayName("POST 请求携带 X-Tenant-Id 时 TenantContext 仍为 1")
    void shouldIgnoreXTenantIdOnPostRequest() throws Exception {
        mvc.perform(post("/api/v1/test/tenant-id")
                        .header("X-Tenant-Id", "777")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().string(String.valueOf(STANDALONE_TENANT_ID)));
    }

    @Test
    @DisplayName("不携带任何租户标识时 TenantContext 仍为 1")
    void shouldReturnStandaloneTenantIdWithNoHeaders() throws Exception {
        mvc.perform(get("/api/v1/test/tenant-id"))
                .andExpect(status().isOk())
                .andExpect(content().string(String.valueOf(STANDALONE_TENANT_ID)));
    }

    // ========== Test Controller ==========

    /**
     * 测试用 Controller，返回当前 TenantContext 中的 tenantId。
     */
    @RestController
    @RequestMapping("/api/v1/test")
    static class TenantEchoController {
        @GetMapping("/tenant-id")
        public String getTenantId() {
            return String.valueOf(TenantContext.getTenantId());
        }

        @PostMapping("/tenant-id")
        public String postTenantId() {
            return String.valueOf(TenantContext.getTenantId());
        }
    }
}
