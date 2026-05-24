package com.campusforum.security.ssrf;

import com.campusforum.infra.security.RedirectFollower;
import com.campusforum.infra.security.SSRFBlockedException;
import com.campusforum.infra.security.SafeHttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SafeHttpClient + RedirectFollower 单元测试（任务 T7.4）。
 *
 * <p>对应 bugfix.md 漏洞 23（SSRF redirect 绕过）的修复验证：</p>
 * <ul>
 *   <li>SafeHttpClient.build 返回的 RestTemplate 在 prepareConnection 阶段会
 *       调用 {@link HttpURLConnection#setInstanceFollowRedirects(boolean)
 *       setInstanceFollowRedirects(false)}，禁用自动 redirect；</li>
 *   <li>RedirectFollower.assertChainIsPublic / nextHop 在每跳前做 host 校验，
 *       命中私网即抛 {@link SSRFBlockedException}。</li>
 * </ul>
 *
 * <p>测试策略：不真正发起 HTTP 请求（避免依赖外网 / 内网真实服务），
 * 通过反射拿到 SafeHttpClient 内部 SimpleClientHttpRequestFactory 的
 * prepareConnection 方法，传入手工构造的 {@code HttpURLConnection} 子类，
 * 观察 {@code setInstanceFollowRedirects(false)} 是否被调用。</p>
 */
class SafeHttpClientNoRedirectTest {

    @Test
    @DisplayName("SafeHttpClient.build 返回的 RestTemplate 在 prepareConnection 阶段会禁用自动 redirect")
    void prepareConnection_disablesInstanceFollowRedirects() throws Exception {
        // 构造客户端：超时不重要，反正本测试不真发请求
        RestTemplate template = SafeHttpClient.build(1000, 1000);

        // 反射：取出 RequestFactory 与 prepareConnection 方法
        ClientHttpRequestFactory factory = template.getRequestFactory();
        assertThat(factory)
                .as("SafeHttpClient.build 必须返回 SimpleClientHttpRequestFactory（含覆写的 prepareConnection）")
                .isInstanceOf(SimpleClientHttpRequestFactory.class);

        Method prepareConnection = factory.getClass().getDeclaredMethod(
                "prepareConnection", HttpURLConnection.class, String.class);
        prepareConnection.setAccessible(true);

        // 构造一个公网域名的假连接：example.com 不会真连，但 host 校验会通过
        RecordingHttpURLConnection conn = new RecordingHttpURLConnection(
                URI.create("https://example.com/anything").toURL());

        // 触发 prepareConnection
        prepareConnection.invoke(factory, conn, "GET");

        // 关键断言：setInstanceFollowRedirects(false) 必被调用
        assertThat(conn.getInstanceFollowRedirects())
                .as("漏洞 23 修复：SafeHttpClient 必须把每个 HttpURLConnection 的 setInstanceFollowRedirects 设为 false")
                .isFalse();
        assertThat(conn.lastSetInstanceFollowRedirectsValue)
                .as("setInstanceFollowRedirects 必须被显式调用过一次")
                .isEqualTo(Boolean.FALSE);
    }

    @Test
    @DisplayName("RedirectFollower.assertChainIsPublic：合法公网链路通过")
    void redirectFollower_publicChain_passes() {
        // 使用 IANA 保留的 example.com（DNS 必能解析到公网 IP）
        URI a = URI.create("https://example.com/v1/chat");
        URI b = URI.create("https://example.org/v1/chat");

        assertThatCode(() -> RedirectFollower.assertChainIsPublic(a, b))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("RedirectFollower.assertChainIsPublic：链中出现私网即抛 SSRFBlockedException")
    void redirectFollower_privateInChain_throws() {
        URI a = URI.create("https://example.com/v1/chat");
        // 用 https 让 scheme 校验通过，然后才检查私网
        URI privateNext = URI.create("https://169.254.169.254/latest/meta-data/");

        assertThatThrownBy(() -> RedirectFollower.assertChainIsPublic(a, privateNext))
                .isInstanceOf(RuntimeException.class)
                .satisfiesAnyOf(
                        ex -> assertThat(ex).hasMessageContaining("私网"),
                        ex -> assertThat(ex).hasMessageContaining("内网"),
                        ex -> assertThat(ex).hasMessageContaining("169.254"),
                        ex -> assertThat(ex).hasMessageContaining("link-local"),
                        ex -> assertThat(ex).hasMessageContaining("禁止指向"),
                        ex -> assertThat(ex).hasMessageContaining("元数据"),
                        ex -> assertThat(ex).hasMessageContaining("回环"),
                        ex -> assertThat(ex).hasMessageContaining("仅允许")
                );
    }

    @Test
    @DisplayName("RedirectFollower.assertChainIsPublic：超过 MAX_HOPS+1 拒绝")
    void redirectFollower_tooManyHops_throws() {
        // 超长链不需要真解析 host —— assertChainIsPublic 在做 host 校验前会先做长度检查；
        // 但实现里"长度检查 vs 逐跳校验"的顺序无强保证，因此这里仍用真实可解析的域名以防万一。
        URI a = URI.create("https://example.com/x");
        URI b = URI.create("https://example.org/x");
        URI c = URI.create("https://example.net/x");
        URI d = URI.create("https://www.iana.org/x");
        URI e = URI.create("https://www.icann.org/x");

        // 5 跳 > MAX_HOPS(3)+1=4 上限
        assertThatThrownBy(() -> RedirectFollower.assertChainIsPublic(a, b, c, d, e))
                .isInstanceOf(SSRFBlockedException.class)
                .hasMessageContaining("跳数超过上限");
    }

    @Test
    @DisplayName("RedirectFollower.assertChainIsPublic：循环跳转抛 SSRFBlockedException")
    void redirectFollower_loop_throws() {
        URI a = URI.create("https://example.com/x");
        URI b = URI.create("https://example.org/x");

        assertThatThrownBy(() -> RedirectFollower.assertChainIsPublic(a, b, a))
                .isInstanceOf(SSRFBlockedException.class)
                .hasMessageContaining("循环跳转");
    }

    @Test
    @DisplayName("RedirectFollower.nextHop：相对 Location 解析后命中私网即抛")
    void redirectFollower_relativeLocation_resolvedAndChecked() {
        URI base = URI.create("https://example.com/v1/chat");

        // 合法相对 Location → 解析后仍指向 example.com
        URI next = RedirectFollower.nextHop(base, "/v2/chat");
        assertThat(next).isEqualTo(URI.create("https://example.com/v2/chat"));

        // 跨域跳到私网：必须抛（用 https 让 scheme 校验先通过）
        assertThatThrownBy(() -> RedirectFollower.nextHop(base, "https://10.0.0.5/admin"))
                .isInstanceOf(RuntimeException.class)
                .satisfiesAnyOf(
                        ex -> assertThat(ex).hasMessageContaining("私网"),
                        ex -> assertThat(ex).hasMessageContaining("内网"),
                        ex -> assertThat(ex).hasMessageContaining("10.0.0"),
                        ex -> assertThat(ex).hasMessageContaining("禁止指向")
                );
    }

    /**
     * 仅用于测试的 HttpURLConnection 子类，记录 {@code setInstanceFollowRedirects} 调用。
     *
     * <p>真实 HttpURLConnection 必须配合一个真实底层连接才能 connect()，
     * 但本测试只关心 prepareConnection 阶段的状态变更，不会触发任何 IO。</p>
     */
    private static final class RecordingHttpURLConnection extends HttpURLConnection {

        Boolean lastSetInstanceFollowRedirectsValue;

        RecordingHttpURLConnection(URL u) {
            super(u);
        }

        @Override
        public void setInstanceFollowRedirects(boolean followRedirects) {
            this.lastSetInstanceFollowRedirectsValue = followRedirects;
            super.setInstanceFollowRedirects(followRedirects);
        }

        @Override public void disconnect() {}
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() {}
    }
}
