package com.campusforum.infra.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * API 文档（Swagger / Knife4j / api-docs）访问控制过滤器。
 *
 * <p>对应 bugfix.md 漏洞 2 / tasks.md T2.2：在加固前，{@code Knife4jConfig}
 * 注册的 {@code /v3/api-docs}、{@code /swagger-ui/} 等路径在生产环境无任何
 * 鉴权保护，攻击者公网拉取 OpenAPI JSON 即可拿到全量接口契约（含管理后台、
 * AI 对话、导出等敏感端点的参数与错误码），形成 Critical 信息泄漏面。</p>
 *
 * <h2>双重校验策略（纵深防御）</h2>
 * <p>本 Filter 在请求进入业务逻辑之前，对所有匹配 {@link #DOC_PATH_PREFIXES}
 * 的 URI 执行两项独立校验，<b>必须同时通过才放行</b>：</p>
 * <ol>
 *   <li><b>Profile 校验</b>：当前任意一个 active profile 必须出现在
 *       {@code security.docs.enabled-profiles} 配置中（默认 {@code [dev, test]}）。
 *       该项是"按部署阶段限制文档可见性"的第一道闸门，等同于
 *       {@code springdoc.api-docs.enabled} 的 Spring 层兜底——即便运维误开
 *       {@code SPRINGDOC_ENABLED=true}，prod profile 不在白名单内仍会被拦截。</li>
 *   <li><b>来源 IP 校验</b>：请求 {@code remoteAddr} 必须命中
 *       {@link TrustedProxyResolver#isFromTrustedProxy(String) 可信反向代理白名单}
 *       （默认包含 127.0.0.1、::1 与三段私有网段）。该项保证即使 dev profile
 *       被错误地部署到公网，外部 IP 仍无法访问文档。</li>
 * </ol>
 *
 * <h2>静默 404 而非 403 的原因</h2>
 * <p>命中拦截分支时统一以 {@code 404 Not Found} 返回（不写响应 body），
 * 而不是 {@code 403 Forbidden}，原因有二：</p>
 * <ul>
 *   <li>避免向攻击者泄漏"该路径存在但被拒绝"信号——403 等同于告诉对方
 *       "去找其他突破口比如管理员账号或更弱的代理"，而 404 让攻击者无法
 *       区分"路径不存在"与"路径被屏蔽"，提高侦察成本；</li>
 *   <li>不写 body（不交给 {@code GlobalExceptionHandler} 接管）能避免
 *       泄漏后端框架信息（{@code R} 包装、错误码命名约定、Spring Boot 版本）。
 *       这与 {@code deploy/nginx/nginx.conf} 中对文档路径
 *       {@code return 404} 的行为保持一致，形成网关 + 应用双重屏蔽。</li>
 * </ul>
 *
 * <h2>Filter 链路顺序</h2>
 * <p>本 Filter 排在 {@link Ordered#HIGHEST_PRECEDENCE} {@code + 1}：</p>
 * <ul>
 *   <li><b>晚于</b> {@code MdcTraceIdFilter}（{@code HIGHEST_PRECEDENCE}）：
 *       那是 traceId 进入 MDC 的入口，本 Filter 内部 {@code log.warn(...)}
 *       需要 traceId 已就绪才能在日志里完成关联——所以是 {@code +1} 而非 {@code +0}。</li>
 *   <li><b>早于</b> {@code TenantResolutionFilter}（{@code HIGHEST_PRECEDENCE + 50}）：
 *       文档路径不需要触发租户解析（命中 {@code DOC_PATH_PREFIXES} 的请求与
 *       业务租户上下文无关），过早进入租户解析会让 {@code TenantResolutionFilter}
 *       对未配置 {@code X-Tenant-Id} 的 swagger 请求报错。</li>
 * </ul>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class DocAccessFilter extends OncePerRequestFilter {

    /**
     * API 文档相关路径前缀清单。
     *
     * <p>覆盖 springdoc 默认 endpoint（{@code /v3/api-docs}）、Swagger UI 静态
     * 资源（{@code /swagger-ui/}、{@code /swagger-ui.html}）、Knife4j 增强 UI
     * （{@code /doc.html}）、对应静态依赖（{@code /webjars/}），以及 Springfox
     * 时代的兼容路径（{@code /swagger-resources}）。任何以这些前缀开头的 URI
     * 都会进入双重校验分支。</p>
     */
    private static final List<String> DOC_PATH_PREFIXES = List.of(
            "/swagger-ui/",
            "/swagger-ui.html",
            "/v3/api-docs",
            "/swagger-resources",
            "/doc.html",
            "/webjars/"
    );

    /** 安全配置项，用于读取 {@code security.docs.enabled-profiles} 白名单。 */
    private final SecurityProperties props;

    /** Spring 环境对象，用于读取当前 active profile 列表。 */
    private final Environment env;

    /** 可信反向代理判定器，复用统一的 CIDR / IPv6 解析逻辑。 */
    private final TrustedProxyResolver trustedProxyResolver;

    /**
     * 入站请求过滤主体。
     *
     * <p>实现要点：</p>
     * <ul>
     *   <li>非文档路径直接放行，零开销；</li>
     *   <li>命中文档路径时执行 profileAllows && fromTrusted 双重校验；</li>
     *   <li>任一校验不通过 → {@code setStatus(404)} 静默返回，不调用
     *       {@code chain.doFilter} 也不写 body，避免泄漏后端信号；</li>
     *   <li>不抛异常——直接 setStatus 后 return，避免被
     *       {@code GlobalExceptionHandler} 接管而暴露 {@code R} 包装结构。</li>
     * </ul>
     */
    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        // 1) URI 不命中文档前缀清单 → 与本 Filter 无关，直接放行
        String uri = req.getRequestURI();
        if (!isDocPath(uri)) {
            chain.doFilter(req, res);
            return;
        }

        // 2) Profile 校验：当前 active profile 与白名单存在交集才允许暴露文档
        boolean profileAllows = isAnyActiveProfileAllowed();

        // 3) 来源 IP 校验：必须来自可信反向代理（含 localhost / 内网网段）
        //    profileAllows 不通过时其实可以短路掉 IP 检查，
        //    但分别求值方便日志追踪具体原因，且耗时可忽略
        boolean fromTrusted = trustedProxyResolver.isFromTrustedProxy(req.getRemoteAddr());

        if (!profileAllows || !fromTrusted) {
            // 静默 404：不写 body，不抛异常，不交给 GlobalExceptionHandler；
            // 与 nginx 层 return 404 行为保持一致，避免泄漏"路径存在但被拒"信号
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            log.debug("DocAccessFilter 拦截文档请求：uri={}, remoteAddr={}, profileAllows={}, fromTrusted={}",
                    uri, req.getRemoteAddr(), profileAllows, fromTrusted);
            return;
        }

        // 4) 双重校验通过 → 放行至下游 Filter / DispatcherServlet
        chain.doFilter(req, res);
    }

    /**
     * 判断请求 URI 是否命中 {@link #DOC_PATH_PREFIXES} 中的任一前缀。
     *
     * @param uri {@link HttpServletRequest#getRequestURI()} 原始值
     * @return 命中任意文档前缀返回 true
     */
    private boolean isDocPath(String uri) {
        if (uri == null || uri.isEmpty()) {
            return false;
        }
        for (String prefix : DOC_PATH_PREFIXES) {
            if (uri.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断当前 active profile 中是否存在被
     * {@code security.docs.enabled-profiles} 白名单允许的项。
     *
     * <p>采用"任一交集即允许"的语义：例如同时启用 {@code dev,test} profile 时，
     * 只要其中之一在白名单内就允许暴露文档；这与 Spring 多 profile 叠加使用
     * 的常见场景一致。</p>
     *
     * @return 存在交集返回 true；active profile 为空 / 白名单为空均返回 false
     */
    private boolean isAnyActiveProfileAllowed() {
        String[] active = env.getActiveProfiles();
        if (active == null || active.length == 0) {
            // 没有显式 profile 时按"严格生产"语义处理：拒绝
            return false;
        }
        List<String> allowed = props.getDocs().getEnabledProfiles();
        if (allowed == null || allowed.isEmpty()) {
            return false;
        }
        Set<String> allowedSet = new HashSet<>(allowed);
        for (String p : active) {
            if (allowedSet.contains(p)) {
                return true;
            }
        }
        return false;
    }
}
