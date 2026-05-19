package com.campusforum.tenant.audit;

import com.campusforum.admin.domain.AuditLog;
import com.campusforum.admin.mapper.AuditLogMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 租户安全审计服务 — 记录越权尝试到 audit_logs 表。
 */
@Service
@RequiredArgsConstructor
public class TenantAuditService {
    private final AuditLogMapper auditLogMapper;

    /**
     * 记录一次租户越权尝试。
     *
     * @param userId         当前登录用户 id
     * @param actualTenantId 用户实际所属租户 id（来自 Session/TenantContext）
     * @param req            当前 HTTP 请求
     * @param reason         拒绝原因标识（如 header_mismatch_session、invalid_tenant_header）
     * @param detail         详细描述
     */
    public void recordViolationAttempt(long userId, long actualTenantId,
                                       HttpServletRequest req, String reason, String detail) {
        AuditLog log = new AuditLog();
        log.setTenantId(actualTenantId);
        log.setOperatorId(userId);
        log.setAction("TENANT_VIOLATION_ATTEMPT");
        log.setTargetType("TENANT");
        log.setIpAddress(getClientIp(req));

        Map<String, Object> d = new LinkedHashMap<>();
        d.put("uri", req.getRequestURI());
        d.put("method", req.getMethod());
        d.put("reason", reason);
        d.put("detail", detail);
        d.put("actualTenantId", actualTenantId);
        try {
            log.setDetail(new ObjectMapper().writeValueAsString(d));
        } catch (JsonProcessingException ignored) {
            // JSON 序列化失败时不阻塞主流程
        }
        auditLogMapper.insert(log);
    }

    private String getClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
