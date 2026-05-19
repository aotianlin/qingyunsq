package com.campusforum.tenant.filter;

import com.campusforum.common.ErrorCode;
import com.campusforum.tenant.TenantContext;
import com.campusforum.tenant.resolver.ResolutionResult;
import com.campusforum.tenant.resolver.TenantNotResolvedException;
import com.campusforum.tenant.resolver.TenantResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TenantResolutionFilter 集成测试。
 *
 * <p>使用 standalone MockMvc 模式验证：
 * <ul>
 *   <li>standalone 模式下 X-Tenant-Id 被忽略，始终使用固定 tenantId</li>
 *   <li>multi 模式下未提供任何来源时返回 400 + TENANT_NOT_RESOLVED</li>
 *   <li>排除路径不经过租户解析</li>
 * </ul>
 */
class TenantResolutionFilterIT {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // ========== Test Controller ==========

    @RestController
    static class TestController {
        @GetMapping("/api/v1/test/tenant-id")
        public String getTenantId() {
            return String.valueOf(TenantContext.getTenantId());
        }

        @GetMapping("/actuator/health")
        public String actuatorHealth() {
            // TenantContext 应为 null（排除路径不设置上下文）
            return "UP";
        }

        @GetMapping("/ws/notify")
        public String wsNotify() {
            return "ws";
        }

        @GetMapping("/swagger-ui/index.html")
        public String swaggerUi() {
            return "swagger";
        }

        @GetMapping("/v3/api-docs/openapi.json")
        public String apiDocs() {
            return "api-docs";
        }
    }

    /**
     * standalone 模式测试：X-Tenant-Id 被忽略
     */
    @Nested
    @DisplayName("standalone 模式")
    class StandaloneModeTest {

        private MockMvc mvc;

        @BeforeEach
        void setUp() {
            // standalone resolver：始终返回固定 tenantId=1
            TenantResolver standaloneResolver = (HttpServletRequest request) ->
                    new ResolutionResult(1L, ResolutionResult.Source.STANDALONE_FIXED, "default");

            TenantResolutionFilter filter = new TenantResolutionFilter(standaloneResolver, OBJECT_MAPPER);

            mvc = MockMvcBuilders.standaloneSetup(new TestController())
                    .addFilters(filter)
                    .build();
        }

        @Test
        @DisplayName("不带 X-Tenant-Id 时使用固定 tenantId")
        void shouldUseFixedTenantIdWithoutHeader() throws Exception {
            mvc.perform(get("/api/v1/test/tenant-id"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("1"));
        }

        @Test
        @DisplayName("带 X-Tenant-Id=999 时仍使用固定 tenantId（header 被忽略）")
        void shouldIgnoreXTenantIdHeader() throws Exception {
            mvc.perform(get("/api/v1/test/tenant-id")
                    .header("X-Tenant-Id", "999"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("1"));
        }

        @Test
        @DisplayName("排除路径 /actuator/ 不经过租户解析，请求正常通过")
        void shouldExcludeActuatorPath() throws Exception {
            mvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("UP"));
        }

        @Test
        @DisplayName("排除路径 /ws/ 不经过租户解析，请求正常通过")
        void shouldExcludeWebSocketPath() throws Exception {
            mvc.perform(get("/ws/notify"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("ws"));
        }
    }

    /**
     * multi 模式测试：未提供任何来源时 400
     */
    @Nested
    @DisplayName("multi 模式")
    class MultiModeTest {

        private MockMvc mvc;

        @BeforeEach
        void setUp() {
            // multi resolver：始终抛出 TenantNotResolvedException（模拟无来源）
            TenantResolver multiResolver = (HttpServletRequest request) -> {
                throw new TenantNotResolvedException(TenantNotResolvedException.Reason.NO_RESOLVER_MATCHED);
            };

            TenantResolutionFilter filter = new TenantResolutionFilter(multiResolver, OBJECT_MAPPER);

            mvc = MockMvcBuilders.standaloneSetup(new TestController())
                    .addFilters(filter)
                    .build();
        }

        @Test
        @DisplayName("未提供任何租户来源时返回 400 + TENANT_NOT_RESOLVED")
        void shouldReturn400WhenNoTenantSource() throws Exception {
            mvc.perform(get("/api/v1/test/tenant-id")
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ErrorCode.TENANT_NOT_RESOLVED.getCode()))
                    .andExpect(jsonPath("$.message").value(ErrorCode.TENANT_NOT_RESOLVED.getMessage()));
        }

        @Test
        @DisplayName("排除路径 /swagger-ui/ 不经过租户解析，请求正常通过")
        void shouldExcludeSwaggerPath() throws Exception {
            mvc.perform(get("/swagger-ui/index.html"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("swagger"));
        }

        @Test
        @DisplayName("排除路径 /v3/api-docs/ 不经过租户解析，请求正常通过")
        void shouldExcludeApiDocsPath() throws Exception {
            mvc.perform(get("/v3/api-docs/openapi.json"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("api-docs"));
        }
    }
}
