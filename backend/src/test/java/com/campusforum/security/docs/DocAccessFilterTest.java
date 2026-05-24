package com.campusforum.security.docs;

import com.campusforum.infra.security.DocAccessFilter;
import com.campusforum.infra.security.SecurityProperties;
import com.campusforum.infra.security.TrustedProxyResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link DocAccessFilter} 单元测试（对应 bugfix.md 漏洞 2 / tasks.md T2.2）。
 *
 * <p>使用纯单元测试 + Mockito：mock {@link HttpServletRequest}、
 * {@link HttpServletResponse}、{@link FilterChain}，
 * 用 {@link MockEnvironment} 切换 active profile，
 * 用 {@link SecurityProperties} 真实 POJO + 默认 {@code Docs.enabledProfiles=[dev,test]}，
 * mock {@link TrustedProxyResolver#isFromTrustedProxy(String)} 决定来源是否可信。
 * 不启动 Spring 上下文，不依赖 MySQL / Redis，符合 T2.2 "纯单元测试 + 编译" 约束。</p>
 */
class DocAccessFilterTest {

    private MockEnvironment env;
    private SecurityProperties props;
    private TrustedProxyResolver trustedProxyResolver;
    private FilterChain chain;
    private HttpServletRequest req;
    private HttpServletResponse res;

    /** 公网外部 IP（不在 SecurityProperties.trusted-proxies 默认白名单内）。 */
    private static final String EXTERNAL_IP = "1.2.3.4";

    /** 内网回环地址（命中默认 trusted-proxies 中的 127.0.0.1）。 */
    private static final String LOCALHOST_IP = "127.0.0.1";

    @BeforeEach
    void setUp() {
        env = new MockEnvironment();

        // 真实 SecurityProperties + 默认 Docs.enabledProfiles=[dev, test]
        // （由 SecurityProperties.Docs 字段初始化器提供，不需要测试再设）
        props = new SecurityProperties();

        // mock TrustedProxyResolver：测试方法按需 stub isFromTrustedProxy
        trustedProxyResolver = mock(TrustedProxyResolver.class);

        chain = mock(FilterChain.class);
        req = mock(HttpServletRequest.class);
        res = mock(HttpServletResponse.class);
    }

    /**
     * 构造一个 DocAccessFilter 实例，注入当前的 props / env / trustedProxyResolver。
     *
     * <p>每次 setUp 后通过本工厂方法生成新实例，保证测试之间相互独立。</p>
     */
    private DocAccessFilter newFilter() {
        return new DocAccessFilter(props, env, trustedProxyResolver);
    }

    /**
     * 触发一次过滤器调用：传入 URI 与 remoteAddr 即可，
     * 隐藏 mock 设置细节让测试主体聚焦于断言。
     */
    private void invokeFilter(String uri, String remoteAddr) throws Exception {
        when(req.getRequestURI()).thenReturn(uri);
        when(req.getRemoteAddr()).thenReturn(remoteAddr);
        newFilter().doFilter(req, res, chain);
    }

    // ---------------- 文档路径 + prod profile 拦截分支 ----------------

    @Test
    @DisplayName("prod_external_returns_404：prod profile 下外部 IP 请求 /v3/api-docs → 404 静默拦截")
    void prod_external_returns_404() throws Exception {
        env.addActiveProfile("prod");
        when(trustedProxyResolver.isFromTrustedProxy(EXTERNAL_IP)).thenReturn(false);

        invokeFilter("/v3/api-docs", EXTERNAL_IP);

        verify(res, times(1)).setStatus(HttpServletResponse.SC_NOT_FOUND);
        // 拦截分支不调用 chain.doFilter
        verifyNoInteractions(chain);
    }

    @Test
    @DisplayName("prod_localhost_returns_404：prod profile 下即便来源 127.0.0.1 仍 404（profile 不允许时 IP 检查无意义）")
    void prod_localhost_returns_404() throws Exception {
        env.addActiveProfile("prod");
        // 即使 isFromTrustedProxy 返回 true，profile 不在白名单内仍应拦截
        when(trustedProxyResolver.isFromTrustedProxy(LOCALHOST_IP)).thenReturn(true);

        invokeFilter("/v3/api-docs", LOCALHOST_IP);

        verify(res, times(1)).setStatus(HttpServletResponse.SC_NOT_FOUND);
        verifyNoInteractions(chain);
    }

    // ---------------- 文档路径 + dev profile 放行 / 拦截分支 ----------------

    @Test
    @DisplayName("dev_localhost_returns_200_passthrough：dev profile + 来源 127.0.0.1 → chain.doFilter 被调用")
    void dev_localhost_returns_200_passthrough() throws Exception {
        env.addActiveProfile("dev");
        when(trustedProxyResolver.isFromTrustedProxy(LOCALHOST_IP)).thenReturn(true);

        invokeFilter("/v3/api-docs", LOCALHOST_IP);

        // 双重校验通过：必须放行到 chain.doFilter，且不应写 404
        verify(chain, times(1)).doFilter(req, res);
        verify(res, never()).setStatus(anyInt());
    }

    @Test
    @DisplayName("dev_externalIp_returns_404：dev profile 但来源外部 IP → 404")
    void dev_externalIp_returns_404() throws Exception {
        env.addActiveProfile("dev");
        when(trustedProxyResolver.isFromTrustedProxy(EXTERNAL_IP)).thenReturn(false);

        invokeFilter("/v3/api-docs", EXTERNAL_IP);

        verify(res, times(1)).setStatus(HttpServletResponse.SC_NOT_FOUND);
        verifyNoInteractions(chain);
    }

    // ---------------- 非文档路径放行 ----------------

    @Test
    @DisplayName("non_doc_path_passthrough：URI 非文档前缀 → 任何 profile 都放行")
    void non_doc_path_passthrough() throws Exception {
        // 即便是最严格的 prod profile + 外部 IP，业务接口也不应被本 Filter 拦截
        env.addActiveProfile("prod");

        invokeFilter("/api/v1/test", EXTERNAL_IP);

        verify(chain, times(1)).doFilter(req, res);
        verify(res, never()).setStatus(anyInt());
        // 非文档路径根本不应触发 trusted-proxy 判断（性能 + 职责单一）
        verifyNoInteractions(trustedProxyResolver);
    }

    // ---------------- 其他文档前缀的回归覆盖 ----------------

    @Test
    @DisplayName("swagger_ui_html_blocked_in_prod：URI = /swagger-ui.html 命中文档前缀 → 走拦截分支")
    void swagger_ui_html_blocked_in_prod() throws Exception {
        env.addActiveProfile("prod");
        when(trustedProxyResolver.isFromTrustedProxy(EXTERNAL_IP)).thenReturn(false);

        invokeFilter("/swagger-ui.html", EXTERNAL_IP);

        verify(res, times(1)).setStatus(HttpServletResponse.SC_NOT_FOUND);
        verifyNoInteractions(chain);
    }
}
