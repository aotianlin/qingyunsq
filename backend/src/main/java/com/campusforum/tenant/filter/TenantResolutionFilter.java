package com.campusforum.tenant.filter;

import com.campusforum.common.ErrorCode;
import com.campusforum.common.R;
import com.campusforum.tenant.TenantContext;
import com.campusforum.tenant.resolver.ResolutionResult;
import com.campusforum.tenant.resolver.TenantNotResolvedException;
import com.campusforum.tenant.resolver.TenantResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 租户解析 Servlet Filter — 替代原 TenantInterceptor。
 *
 * <p>优先级高于 Sa-Token 拦截器，在请求进入 DispatcherServlet 之前完成租户上下文设置。
 * 解析失败时直接写 JSON 错误响应，不进入 Spring MVC 流程。</p>
 */
@Component
@RequiredArgsConstructor
public class TenantResolutionFilter extends OncePerRequestFilter {

    private final TenantResolver resolver;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        // 排除路径：actuator (仅 localhost), swagger, api-docs, ws upgrade（WS 走 HandshakeInterceptor）
        if (isExcluded(req)) {
            chain.doFilter(req, res);
            return;
        }

        try {
            ResolutionResult result = resolver.resolve(req);
            TenantContext.setTenantId(result.tenantId());
            TenantContext.setTenantCode(result.tenantCode());
            chain.doFilter(req, res);
        } catch (TenantNotResolvedException e) {
            writeError(res, HttpStatus.BAD_REQUEST, ErrorCode.TENANT_NOT_RESOLVED, e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }

    private boolean isExcluded(HttpServletRequest req) {
        String uri = req.getRequestURI();
        if (uri.startsWith("/actuator/")) {
            // 安全加固（缺陷 1.28）：actuator 仅本地访问；
            // 外部访问由 nginx 层拦截返回 404，这里作为后端兜底。
            String remote = req.getRemoteAddr();
            return "127.0.0.1".equals(remote)
                    || "::1".equals(remote)
                    || "0:0:0:0:0:0:0:1".equals(remote);
        }
        return uri.startsWith("/swagger-ui/")
                || uri.startsWith("/v3/api-docs/")
                || uri.startsWith("/ws/");
    }

    private void writeError(HttpServletResponse res, HttpStatus status,
                            ErrorCode code, String detail) throws IOException {
        res.setStatus(status.value());
        res.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(res.getWriter(), R.fail(code));
    }
}
