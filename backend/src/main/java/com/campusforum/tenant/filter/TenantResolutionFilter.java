package com.campusforum.tenant.filter;

import com.campusforum.common.ErrorCode;
import com.campusforum.common.R;
import com.campusforum.infra.web.MdcTraceIdFilter;
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
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 租户解析 Servlet Filter — 替代原 TenantInterceptor。
 *
 * <p>优先级高于 Sa-Token 拦截器，在请求进入 DispatcherServlet 之前完成租户上下文设置。
 * 解析失败时直接写 JSON 错误响应，不进入 Spring MVC 流程。</p>
 *
 * <h2>与 DocAccessFilter 的链路关系（漏洞 2 / T2.3）</h2>
 * <p>本 Filter 排在 {@code Ordered.HIGHEST_PRECEDENCE + 50} 附近，
 * 而 {@code DocAccessFilter} 排在 {@code Ordered.HIGHEST_PRECEDENCE + 1}，
 * 即 <b>DocAccessFilter 在更早阶段对 swagger / api-docs 路径完成裁决</b>：</p>
 * <ul>
 *   <li>profile + 来源 IP 不通过 → DocAccessFilter 直接 {@code setStatus(404)} 并 return，
 *       请求根本不会走到 TenantResolutionFilter；</li>
 *   <li>profile + 来源 IP 通过 → DocAccessFilter 调 {@code chain.doFilter}，
 *       请求继续向后传递，此时 TenantResolutionFilter 才会看到该路径。</li>
 * </ul>
 * <p>因此本 Filter 中 {@link #isExcluded(HttpServletRequest)} 对文档路径的"放行"
 * 仅是<b>"已由 DocAccessFilter 裁决，无需再做租户解析"的标记</b>，而不是权限放行；
 * 真正的访问控制始终在 DocAccessFilter 完成。这样设计的好处是文档路径不需要
 * X-Tenant-Id / 子域名解析，避免对 Swagger UI 静态资源请求误报 TENANT_NOT_RESOLVED。</p>
 */
@Component
@RequiredArgsConstructor
public class TenantResolutionFilter extends OncePerRequestFilter {

    private final TenantResolver resolver;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        // 排除路径：actuator (仅 localhost), swagger / api-docs / webjars / doc.html / swagger-resources
        // (已由 DocAccessFilter 裁决), ws upgrade（WS 走 HandshakeInterceptor）
        if (isExcluded(req)) {
            chain.doFilter(req, res);
            return;
        }

        try {
            ResolutionResult result = resolver.resolve(req);
            TenantContext.setTenantId(result.tenantId());
            TenantContext.setTenantCode(result.tenantCode());
            // 同步写入 MDC，让日志 pattern 中的 %X{tenantId} 可见；
            // 清理由 MdcTraceIdFilter 在请求结束时统一负责（finally 中 MDC.remove），
            // 这里不再做 finally 清理，避免与上层 Filter 重复。
            MDC.put(MdcTraceIdFilter.MDC_TENANT_ID, String.valueOf(result.tenantId()));
            chain.doFilter(req, res);
        } catch (TenantNotResolvedException e) {
            writeError(res, HttpStatus.BAD_REQUEST, ErrorCode.TENANT_NOT_RESOLVED, e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * 判断请求 URI 是否属于"无需租户解析"的路径白名单。
     *
     * <p>此处的"放行"语义并不等同于权限放行，分三类来源：</p>
     * <ol>
     *   <li><b>{@code /actuator/}</b>：管理端点，仅允许 localhost / IPv6 环回访问；
     *       外部访问由 nginx 层与本方法返回 false 的两道防线兜底（缺陷 1.28）。
     *       此分支保持现有逻辑不变。</li>
     *
     *   <li><b>API 文档相关路径</b>（漏洞 2 / T2.3）：
     *       {@code /swagger-ui/}、{@code /swagger-ui.html}、{@code /v3/api-docs}、
     *       {@code /swagger-resources}、{@code /doc.html}、{@code /webjars/}。
     *       这些路径已由 {@code DocAccessFilter}（{@code @Order(Ordered.HIGHEST_PRECEDENCE + 1)}）
     *       在更早阶段完成 profile + 来源 IP 双重校验：
     *       <ul>
     *         <li>未通过 → DocAccessFilter 已 {@code setStatus(404)} 并 return，
     *             请求根本不会走到本方法；</li>
     *         <li>通过 → 才会走到这里，本方法返回 true 仅是<b>"无需走租户解析"的标记</b>，
     *             避免 Swagger UI 等静态资源因没有 X-Tenant-Id / 子域名而被
     *             {@code TenantResolver} 拒绝（TENANT_NOT_RESOLVED）。</li>
     *       </ul>
     *       <b>路径列表与 {@code DocAccessFilter.DOC_PATH_PREFIXES} 保持一致（共 6 项）</b>，
     *       两处需同步增减。</li>
     *
     *   <li><b>{@code /ws/}</b>：WebSocket 升级请求走 {@code TenantHandshakeInterceptor}
     *       做握手期租户绑定，与 HTTP 请求的 Filter 链解耦。此分支保持现有逻辑不变。</li>
     * </ol>
     *
     * @param req 入站 HTTP 请求
     * @return 命中以上任一类即返回 true，跳过租户解析
     */
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
        // API 文档路径（与 DocAccessFilter.DOC_PATH_PREFIXES 一一对应，共 6 项）：
        // 进入这里时已经通过了 DocAccessFilter 的 profile + IP 校验，本 Filter
        // 只需把它们当作"无需租户解析"的路径直接放行即可。
        if (uri.startsWith("/swagger-ui/")
                || uri.startsWith("/swagger-ui.html")
                || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/swagger-resources")
                || uri.startsWith("/doc.html")
                || uri.startsWith("/webjars/")) {
            return true;
        }
        // WebSocket 握手请求由 TenantHandshakeInterceptor 处理，HTTP Filter 链不参与。
        return uri.startsWith("/ws/");
    }

    private void writeError(HttpServletResponse res, HttpStatus status,
                            ErrorCode code, String detail) throws IOException {
        res.setStatus(status.value());
        res.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(res.getWriter(), R.fail(code));
    }
}
