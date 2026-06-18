package com.campusforum.tenant.audit;

import com.campusforum.admin.domain.AuditLog;
import com.campusforum.admin.mapper.AuditLogMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 租户安全审计服务 — 记录越权尝试和管理操作到 audit_logs 表。
 *
 * <p>两个公开方法都用 {@code @Async("auditExecutor")} 异步落库，避免阻塞业务请求线程。
 * 跨线程不持有 HttpServletRequest（Tomcat 在 controller 返回后回收 req），所有需要的请求信息
 * 由调用方提前提取后以 String 形式传入。
 */
@Service
@RequiredArgsConstructor
public class TenantAuditService {
    /** 复用单例 ObjectMapper：线程安全且构造开销不低，避免每次审计都 new。 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AuditLogMapper auditLogMapper;

    /**
     * 记录一次租户越权尝试（异步）。
     *
     * @param userId         当前登录用户 id
     * @param actualTenantId 用户实际所属租户 id
     * @param uri            请求 URI（由调用方提前提取）
     * @param method         HTTP method（由调用方提前提取）
     * @param ipAddress      客户端 IP（由调用方调 {@link #resolveClientIp} 提取）
     * @param reason         拒绝原因标识
     * @param detail         详细描述
     */
    @Async("auditExecutor")
    public void recordViolationAttempt(long userId, long actualTenantId,
                                       String uri, String method, String ipAddress,
                                       String reason, String detail) {
        AuditLog log = new AuditLog();
        log.setTenantId(actualTenantId);
        log.setOperatorId(userId);
        log.setAction("TENANT_VIOLATION_ATTEMPT");
        log.setTargetType("TENANT");
        log.setIpAddress(ipAddress);

        Map<String, Object> d = new LinkedHashMap<>();
        d.put("uri", uri);
        d.put("method", method);
        d.put("reason", reason);
        d.put("detail", detail);
        d.put("actualTenantId", actualTenantId);
        try {
            log.setDetail(OBJECT_MAPPER.writeValueAsString(d));
        } catch (JsonProcessingException ignored) {
            // JSON 序列化失败时不阻塞主流程
        }
        auditLogMapper.insert(log);
    }

    /**
     * 记录一次管理操作（异步，如租户配置变更、敏感凭证修改）。
     *
     * @param operatorId 操作者用户 id
     * @param tenantId   操作所属租户 id
     * @param ipAddress  客户端 IP（由调用方调 {@link #resolveClientIp} 提取）
     * @param action     操作标识（如 AI_CONFIG_UPDATE）
     * @param targetType 目标类型（如 TENANT、USER、POST）
     * @param targetId   目标 id
     * @param detail     操作详情，敏感字段调用方应自行脱敏后传入
     */
    @Async("auditExecutor")
    public void recordAdminAction(Long operatorId, Long tenantId, String ipAddress,
                                   String action, String targetType, Long targetId,
                                   Map<String, Object> detail) {
        AuditLog log = new AuditLog();
        log.setTenantId(tenantId);
        log.setOperatorId(operatorId);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setIpAddress(ipAddress);
        try {
            log.setDetail(OBJECT_MAPPER.writeValueAsString(detail));
        } catch (JsonProcessingException ignored) {
            // JSON 序列化失败时不阻塞主流程
        }
        auditLogMapper.insert(log);
    }

    /**
     * 从请求中提取真实客户端 IP（优先 X-Forwarded-For 头）。
     * 必须在调用方（拦截器/controller）线程中调用，不能在 @Async 任务里调（req 可能已被 Tomcat 回收）。
     */
    public static String resolveClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
