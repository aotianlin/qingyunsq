package com.campusforum.infra.web;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * traceId / tenantId / userId 写入 SLF4J MDC 的入站 Filter。
 *
 * <p>背景（对应 bugfix.md 漏洞 31）：在加固前，{@link com.campusforum.common.R} 的
 * {@code traceId} 由每次构造 {@code R} 时现场生成（{@code UUID.randomUUID().substring(0,8)}），
 * 与 SLF4J MDC 中的 traceId 完全无关；后端日志在跨请求排查时无法和响应体的 traceId 对齐，
 * 客户端拿到的 traceId 也无法定位到真实日志条目。</p>
 *
 * <p>本 Filter 在请求进入业务逻辑之前完成以下职责：</p>
 * <ul>
 *   <li>读取入站 {@code X-Trace-Id} 请求头。若头部值满足正则
 *       {@code ^[a-zA-Z0-9-]{8,64}$} 即视为合法 traceId 直接透传，使分布式
 *       场景（前端注入 / 网关注入 / 服务间调用）的 traceId 能贯通后端；
 *       否则按 16 字符 UUID 重新生成（去掉连字符后取前 16 位）。</li>
 *   <li>把 traceId 写入 {@link MDC}（key={@code traceId}），同时写入响应头
 *       {@code X-Trace-Id}，让 {@link com.campusforum.common.R#getTraceId()} 与
 *       服务端日志中的 {@code %X{traceId}} 共用同一个值。</li>
 *   <li>当用户已登录时，把 {@code StpUtil.getLoginIdAsLong()} 写入 MDC.userId；
 *       Sa-Token 在未登录或异常状态可能抛 {@code NotLoginException}，因此用
 *       try-catch 包裹避免影响主流程。</li>
 *   <li>在 finally 块统一清理 MDC 三个 key（{@code traceId}/{@code tenantId}/{@code userId}），
 *       防止线程池复用导致的上下文串味。{@code tenantId} 由
 *       {@link com.campusforum.tenant.filter.TenantResolutionFilter} 在解析
 *       租户后写入 MDC，本 Filter 在响应返回时统一清理。</li>
 * </ul>
 *
 * <p>排序：{@code @Order(Ordered.HIGHEST_PRECEDENCE)} 确保最先执行，先于
 * {@code TenantResolutionFilter}、{@code DocAccessFilter} 等业务 Filter，
 * 让所有过滤器内部 {@code log.info(...)} 都能携带 traceId。</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcTraceIdFilter extends OncePerRequestFilter {

    /** MDC 中 traceId 的 key 名称。 */
    public static final String MDC_TRACE_ID = "traceId";

    /** MDC 中 tenantId 的 key 名称。 */
    public static final String MDC_TENANT_ID = "tenantId";

    /** MDC 中 userId 的 key 名称。 */
    public static final String MDC_USER_ID = "userId";

    /** 入站 / 出站 traceId HTTP 头名称。 */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    /**
     * 入站 traceId 合法字符与长度校验：仅允许字母、数字与连字符，长度 8-64。
     * 不满足即视为客户端伪造 / 异常输入，由本 Filter 重新生成。
     */
    private static final Pattern VALID_TRACE_ID = Pattern.compile("^[a-zA-Z0-9-]{8,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // 1) 解析 traceId：优先复用合法的入站头，否则现场生成 16 字符 UUID
        String traceId = resolveTraceId(request.getHeader(HEADER_TRACE_ID));

        try {
            // 2) 写入 MDC + 响应头
            MDC.put(MDC_TRACE_ID, traceId);
            response.setHeader(HEADER_TRACE_ID, traceId);

            // 3) 已登录用户：把 userId 写入 MDC，便于按用户排查问题
            //    Sa-Token 在未登录时会抛 NotLoginException，此处不能让它影响主流程
            tryPutUserId();

            chain.doFilter(request, response);
        } finally {
            // 4) 统一清理 MDC，避免线程池复用导致跨请求上下文串味
            MDC.remove(MDC_TRACE_ID);
            MDC.remove(MDC_TENANT_ID);
            MDC.remove(MDC_USER_ID);
        }
    }

    /**
     * 解析最终使用的 traceId。
     *
     * <p>合法入站头直接透传；非法 / 缺失则生成新的 16 字符 UUID（去连字符取前 16 位）。</p>
     *
     * @param incoming 入站 {@code X-Trace-Id} 头部原始值，可能为 null
     * @return 最终采用的 traceId
     */
    private String resolveTraceId(String incoming) {
        if (incoming != null && VALID_TRACE_ID.matcher(incoming).matches()) {
            return incoming;
        }
        // 16 字符 UUID：满足正则 ^[a-zA-Z0-9-]{8,64}$，长度足以避免分布式碰撞
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 尽力尝试把当前登录用户 ID 写入 MDC，未登录或 Sa-Token 异常时静默跳过。
     *
     * <p>Sa-Token 静态方法在测试 / 异步场景下可能抛各类运行时异常（如
     * {@code NotLoginException}、上下文未初始化等），这里用宽口 catch 兜底，
     * 仅 debug 级别记录，避免影响请求主流程。</p>
     */
    private void tryPutUserId() {
        try {
            if (StpUtil.isLogin()) {
                MDC.put(MDC_USER_ID, String.valueOf(StpUtil.getLoginIdAsLong()));
            }
        } catch (Throwable t) {
            // 故意吞掉异常：MDC 仅用于日志增强，不应反向阻断业务请求
            log.debug("MdcTraceIdFilter 写入 userId 失败：{}", t.getMessage());
        }
    }
}
