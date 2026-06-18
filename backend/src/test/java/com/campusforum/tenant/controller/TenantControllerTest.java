package com.campusforum.tenant.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.admin.domain.AuditLog;
import com.campusforum.admin.mapper.AuditLogMapper;
import com.campusforum.tenant.audit.TenantAuditService;
import com.campusforum.tenant.service.TenantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TenantController AI 配置接口单元测试。
 *
 * 覆盖：
 * F2:
 *  - GET /ai-config 返回的 apiKey 必须脱敏（含 "***"）
 *  - PUT /ai-config 传入含 *** 的占位符 → 跳过 apiKey 更新（service 收到 null）
 *  - PUT /ai-config 传入真实 apiKey → 正常更新
 *
 * F6（audit diff）:
 *  - audit detail 结构是 {"changes": {...}}，只记变化字段
 *  - apiKey 在 changes 中双方都用掩码格式（不能含原始 key）
 *  - 完全没改任何字段时 changes 为空 {}
 *  - apiKey 传脱敏占位符时不进 changes（视为未变）
 */
@ExtendWith(MockitoExtension.class)
class TenantControllerTest {

    @Mock
    private TenantService tenantService;

    @Mock
    private AuditLogMapper auditLogMapper;

    private TenantAuditService auditService;
    private TenantController controller;

    @BeforeEach
    void setUp() {
        auditService = new TenantAuditService(auditLogMapper);
        controller = new TenantController(tenantService, auditService);
    }

    // --- F2: GET 脱敏 ---

    @Test
    void getAiConfig_shouldReturnMaskedApiKey() {
        Map<String, Object> rawConfig = new HashMap<>();
        rawConfig.put("provider", "openai");
        rawConfig.put("baseUrl", "https://api.deepseek.com");
        rawConfig.put("model", "deepseek-v4-pro");
        rawConfig.put("apiKey", "sk-test-placeholder-1234567890abcdef");
        when(tenantService.getAiConfig(1L)).thenReturn(rawConfig);

        var result = controller.getAiConfig(1L);

        Map<String, Object> data = result.getData();
        assertThat(data.get("apiKey")).isEqualTo("sk-t***cdef");
        assertThat(data.get("provider")).isEqualTo("openai");
        assertThat(data.get("baseUrl")).isEqualTo("https://api.deepseek.com");
        assertThat(data.get("model")).isEqualTo("deepseek-v4-pro");
    }

    @Test
    void getAiConfig_shouldHandleEmptyApiKey() {
        Map<String, Object> rawConfig = new HashMap<>();
        rawConfig.put("provider", "mock");
        rawConfig.put("apiKey", "");
        when(tenantService.getAiConfig(1L)).thenReturn(rawConfig);

        var result = controller.getAiConfig(1L);
        assertThat(result.getData().get("apiKey")).isEqualTo("<empty>");
    }

    // --- F2 + F6: PUT 占位符识别 + diff ---

    @Test
    void updateAiConfig_shouldSkipApiKeyUpdateAndExcludeFromDiffWhenPlaceholder() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("127.0.0.1");

        // 旧 cfg：保留所有字段
        when(tenantService.getAiConfig(1L)).thenReturn(Map.of(
                "provider", "openai",
                "baseUrl", "https://api.deepseek.com",
                "apiKey", "sk-test-placeholder-1234567890abcdef",
                "model", "deepseek-v4-pro"
        ));

        Map<String, String> body = Map.of(
                "provider", "openai",
                "baseUrl", "https://api.deepseek.com",
                "apiKey", "sk-t***cdef",   // 掩码占位符
                "model", "deepseek-v4-pro");

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            controller.updateAiConfig(1L, body, req);
        }

        // service 收到 apiKey=null（跳过更新）
        verify(tenantService).updateAiConfig(eq(1L),
                eq("openai"), eq("https://api.deepseek.com"), isNull(), eq("deepseek-v4-pro"));

        // diff: 没改任何字段 → changes 应为空 {}
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insert(captor.capture());
        String detail = captor.getValue().getDetail();
        assertThat(detail).contains("\"changes\":{}");
        // apiKey 原始值不应出现在审计中
        assertThat(detail).doesNotContain("sk-test-placeholder-1234567890abcdef");
    }

    @Test
    void updateAiConfig_shouldRecordOnlyChangedFieldsInDiff() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("127.0.0.1");

        when(tenantService.getAiConfig(1L)).thenReturn(Map.of(
                "provider", "openai",
                "baseUrl", "https://api.deepseek.com",
                "apiKey", "sk-test-placeholder-1234567890abcdef",
                "model", "deepseek-v4-pro"
        ));

        // 只改 model
        Map<String, String> body = Map.of(
                "provider", "openai",
                "baseUrl", "https://api.deepseek.com",
                "apiKey", "sk-t***cdef",
                "model", "deepseek-v4-flash");

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            controller.updateAiConfig(1L, body, req);
        }

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insert(captor.capture());
        String detail = captor.getValue().getDetail();
        // changes 只含 model
        assertThat(detail)
                .contains("\"changes\":")
                .contains("\"model\":")
                .contains("\"from\":\"deepseek-v4-pro\"")
                .contains("\"to\":\"deepseek-v4-flash\"");
        // 没改的字段不进 changes
        assertThat(detail).doesNotContain("\"provider\":{");
        assertThat(detail).doesNotContain("\"baseUrl\":{");
    }

    @Test
    void updateAiConfig_shouldMaskApiKeyInDiffWhenChanged() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("127.0.0.1");

        when(tenantService.getAiConfig(1L)).thenReturn(Map.of(
                "provider", "openai",
                "baseUrl", "https://api.deepseek.com",
                "apiKey", "sk-OLDOLDOLD12345678901234OLDOLDOLD",
                "model", "deepseek-v4-pro"
        ));

        // 真的换了 apiKey
        Map<String, String> body = Map.of(
                "provider", "openai",
                "baseUrl", "https://api.deepseek.com",
                "apiKey", "sk-NEWNEWNEW12345678901234NEWNEWNEW",
                "model", "deepseek-v4-pro");

        try (MockedStatic<StpUtil> stpMock = mockStatic(StpUtil.class)) {
            stpMock.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            controller.updateAiConfig(1L, body, req);
        }

        // service 收到真实新 key
        verify(tenantService).updateAiConfig(eq(1L),
                eq("openai"), eq("https://api.deepseek.com"),
                eq("sk-NEWNEWNEW12345678901234NEWNEWNEW"), eq("deepseek-v4-pro"));

        // detail: apiKey 字段在 changes 中，新旧都是掩码
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insert(captor.capture());
        String detail = captor.getValue().getDetail();
        assertThat(detail).contains("\"apiKey\":")
                .contains("\"from\":\"sk-O***DOLD\"")
                .contains("\"to\":\"sk-N***WNEW\"");
        // 明文绝不能进 audit
        assertThat(detail)
                .doesNotContain("sk-OLDOLDOLD12345678901234")
                .doesNotContain("sk-NEWNEWNEW12345678901234");
    }

    private static <T> MockedStatic<T> mockStatic(Class<T> clazz) {
        return org.mockito.Mockito.mockStatic(clazz);
    }
}
