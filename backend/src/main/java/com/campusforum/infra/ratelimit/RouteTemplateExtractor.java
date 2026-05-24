package com.campusforum.infra.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;

/**
 * 从 {@link HandlerMapping} 提取当前请求的"路由模板"（含 path variable 的原始 pattern）。
 *
 * <p>对应 bugfix.md 漏洞 7：原 {@code RateLimitInterceptor} 用
 * {@code request.getRequestURI()} 做限流 key，含 path variable 的端点
 * （如 {@code /api/v1/posts/{id}}）会让每个具体 ID 形成独立桶，攻击者通过轮询不同 ID
 * 即可绕过单端点限流。切换到由 Spring MVC 暴露的"路由模板"作为 key 后，
 * N 个不同 ID 的请求共享同一个桶，恢复"按端点限流"的语义。</p>
 *
 * <p>该组件被设计为薄包装：仅读取 Spring MVC 在 {@code DispatcherServlet} 阶段
 * 通过 {@link HandlerMapping#BEST_MATCHING_PATTERN_ATTRIBUTE} 写入的 attribute，
 * 不依赖 {@code RequestMappingHandlerMapping} 实例本身，便于在拦截器
 * {@code preHandle} 中安全调用（此时 attribute 已被填充）。</p>
 */
@Component
public class RouteTemplateExtractor {

    /**
     * 从请求中提取路由模板。
     *
     * <p>读取顺序：</p>
     * <ol>
     *   <li>优先取 {@link HandlerMapping#BEST_MATCHING_PATTERN_ATTRIBUTE}，命中视为模板成功；</li>
     *   <li>未命中时（如 404、静态资源、过滤器链短路等场景）退回到 {@code request.getRequestURI()}，
     *       并将 {@code isTemplate} 置为 {@code false}，由调用方按"更严格的 fallback"处理
     *       （例如把限流配额减半）。</li>
     * </ol>
     *
     * @param request 当前 HTTP 请求，必须非空
     * @return 提取结果，{@code key} 不会为 {@code null}
     */
    public ExtractResult extract(HttpServletRequest request) {
        // BEST_MATCHING_PATTERN_ATTRIBUTE 由 Spring MVC 内部在路由匹配阶段写入
        // 拦截器 preHandle 阶段读取时已经被填充
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (pattern instanceof String s && !s.isBlank()) {
            return new ExtractResult(s, true);
        }
        // 兜底：路由未匹配时使用原始 URI，但同时把 isTemplate=false 透出给调用方，
        // 由限流拦截器决定是否走更严格的兜底配额
        String uri = request.getRequestURI();
        return new ExtractResult(uri == null ? "" : uri, false);
    }

    /**
     * 提取结果：限流 key 与该 key 是否真正来自路由模板。
     *
     * <p>{@code isTemplate=false} 时表示退化到原始 URI，调用方应使用更严格的 fallback
     * （例如配额减半），避免新模式失效时仍能兜底防御。</p>
     *
     * @param key        参与拼接限流 key 的路径片段，永远非 null
     * @param isTemplate 是否成功取得 Spring MVC 路由模板
     */
    public record ExtractResult(String key, boolean isTemplate) {
    }
}
