package com.campusforum.tenant.interceptor;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.common.ErrorCode;
import com.campusforum.common.R;
import com.campusforum.tenant.TenantContext;
import com.campusforum.tenant.audit.TenantAuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * 已认证请求的租户绑定校验拦截器。
 *
 * <p>在 Sa-Token 认证之后运行，检测客户端发送的 X-Tenant-Id 头
 * 与当前 TenantContext（来自 Session 权威来源）是否一致。
 * 不一致时返回 403 并记录审计日志。</p>
 */
@Component
@RequiredArgsConstructor
public class TenantBindingCheckInterceptor implements HandlerInterceptor {
    private final TenantAuditService auditService;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler)
            throws Exception {
        // 未登录用户不做绑定校验
        if (!StpUtil.isLogin()) {
            return true;
        }

        String headerVal = req.getHeader("X-Tenant-Id");
        // 不发 X-Tenant-Id 头则不检查（正常已认证请求无需携带）
        if (headerVal == null) {
            return true;
        }

        long claimedByClient;
        try {
            claimedByClient = Long.parseLong(headerVal);
        } catch (NumberFormatException e) {
            // 非法值视作越权尝试
            return reject(req, res, "invalid_tenant_header", headerVal);
        }

        long actual = TenantContext.getTenantId();
        if (claimedByClient != actual) {
            return reject(req, res, "header_mismatch_session",
                    claimedByClient + " vs session " + actual);
        }
        return true;
    }

    private boolean reject(HttpServletRequest req, HttpServletResponse res,
                           String reason, String detail) throws IOException {
        long userId = StpUtil.getLoginIdAsLong();
        long actualTid = TenantContext.getTenantId();
        auditService.recordViolationAttempt(userId, actualTid, req, reason, detail);
        res.setStatus(HttpStatus.FORBIDDEN.value());
        res.setContentType("application/json;charset=UTF-8");
        new ObjectMapper().writeValue(res.getWriter(), R.fail(ErrorCode.TENANT_VIOLATION));
        return false;
    }
}
