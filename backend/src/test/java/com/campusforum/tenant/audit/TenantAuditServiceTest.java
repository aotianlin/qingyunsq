package com.campusforum.tenant.audit;

import com.campusforum.admin.domain.AuditLog;
import com.campusforum.admin.mapper.AuditLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * TenantAuditService 单元测试。
 *
 * 覆盖：
 * - recordAdminAction 把传入字段正确映射到 AuditLog（F6 审计落库正确）
 * - 跨线程安全：签名接收 String 类型 ipAddress，不持有 HttpServletRequest（F7）
 * - resolveClientIp 静态工具方法在调用方线程中正确解析 IP（X-Forwarded-For 优先）
 * - JSON 序列化失败时不阻塞主流程
 */
@ExtendWith(MockitoExtension.class)
class TenantAuditServiceTest {

    @Mock
    private AuditLogMapper auditLogMapper;

    @Test
    void recordAdminAction_shouldMapFieldsCorrectly() {
        TenantAuditService svc = new TenantAuditService(auditLogMapper);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("provider", "openai");
        detail.put("baseUrl", "https://api.deepseek.com");
        detail.put("model", "deepseek-v4-pro");
        detail.put("apiKey", "sk-a***xyz");

        svc.recordAdminAction(7L, 1L, "10.20.30.40",
                "AI_CONFIG_UPDATE", "TENANT", 1L, detail);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insert(captor.capture());

        AuditLog log = captor.getValue();
        assertThat(log.getTenantId()).isEqualTo(1L);
        assertThat(log.getOperatorId()).isEqualTo(7L);
        assertThat(log.getAction()).isEqualTo("AI_CONFIG_UPDATE");
        assertThat(log.getTargetType()).isEqualTo("TENANT");
        assertThat(log.getTargetId()).isEqualTo(1L);
        assertThat(log.getIpAddress()).isEqualTo("10.20.30.40");
        assertThat(log.getDetail())
                .contains("\"provider\":\"openai\"")
                .contains("\"baseUrl\":\"https://api.deepseek.com\"")
                .contains("\"model\":\"deepseek-v4-pro\"")
                .contains("\"apiKey\":\"sk-a***xyz\"");
    }

    @Test
    void resolveClientIp_shouldPreferXForwardedForOverRemoteAddr() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.20.30.40");
        req.addHeader("X-Forwarded-For", "203.0.113.5, 10.20.30.40");

        assertThat(TenantAuditService.resolveClientIp(req)).isEqualTo("203.0.113.5");
    }

    @Test
    void resolveClientIp_shouldFallbackToRemoteAddrWhenNoHeader() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.20.30.40");

        assertThat(TenantAuditService.resolveClientIp(req)).isEqualTo("10.20.30.40");
    }

    @Test
    void recordAdminAction_shouldTolerateUnserializableDetail() {
        // detail 序列化失败不应阻塞主流程
        TenantAuditService svc = new TenantAuditService(auditLogMapper);

        // 自引用循环触发 Jackson 序列化异常
        Map<String, Object> bad = new LinkedHashMap<>();
        bad.put("self", bad);

        svc.recordAdminAction(7L, 1L, "127.0.0.1",
                "AI_CONFIG_UPDATE", "TENANT", 1L, bad);

        // 即便 detail 写入失败，仍然 insert 一条记录（其它字段保留）
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("AI_CONFIG_UPDATE");
    }

    @Test
    void recordViolationAttempt_shouldMapFieldsCorrectly() {
        TenantAuditService svc = new TenantAuditService(auditLogMapper);

        svc.recordViolationAttempt(7L, 1L,
                "/api/v1/posts/42", "PUT", "10.20.30.40",
                "header_mismatch_session", "claimed 9 vs session 1");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insert(captor.capture());

        AuditLog log = captor.getValue();
        assertThat(log.getAction()).isEqualTo("TENANT_VIOLATION_ATTEMPT");
        assertThat(log.getTenantId()).isEqualTo(1L);
        assertThat(log.getOperatorId()).isEqualTo(7L);
        assertThat(log.getIpAddress()).isEqualTo("10.20.30.40");
        assertThat(log.getDetail())
                .contains("\"uri\":\"/api/v1/posts/42\"")
                .contains("\"method\":\"PUT\"")
                .contains("\"reason\":\"header_mismatch_session\"");
    }
}
