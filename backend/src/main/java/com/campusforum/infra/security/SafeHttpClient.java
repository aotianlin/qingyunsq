package com.campusforum.infra.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 防 SSRF / DNS 重绑定 的 RestTemplate 工厂。
 *
 * <p>背景：{@link PrivateNetworkValidator#requirePublic(String, boolean)} 在<b>校验阶段</b>
 * 解析过一次 DNS，但 RestTemplate 后续真正连接时再次解析 — 攻击者可通过低 TTL 恶意 DNS
 * 让两次解析返回不同 IP（校验阶段返回公网 IP，连接阶段返回内网 IP，如 {@code 169.254.169.254}）。</p>
 *
 * <p>本工厂在 {@link SimpleClientHttpRequestFactory#prepareConnection} 钩子中
 * 对目标 URL 的 host 再次执行解析与私网判断；若任何返回的 IP 命中私网则抛
 * {@link SSRFBlockedException}，连接阶段直接终止。</p>
 *
 * <p>注意：该方案不能 100% 杜绝 DNS 重绑定（连接阶段的解析与 prepareConnection 中的
 * 解析仍有微秒级时间差），但攻击窗口极小，对绝大多数实际攻击是有效防御。</p>
 *
 * <p><b>漏洞 23 修复（T7.4）：禁用自动 redirect</b>。原实现 RestTemplate 默认会自动
 * 跟随 3xx 重定向，攻击者可借合法公网 host 返回 302 让客户端再请求私网地址
 * （如 {@code Location: http://169.254.169.254/latest/meta-data/}），绕过
 * {@code prepareConnection} 阶段的 host 校验。本工厂在每个连接上调用
 * {@link HttpURLConnection#setInstanceFollowRedirects(boolean) setInstanceFollowRedirects(false)}，
 * 让上层业务代码遇到 3xx 时立即得到 304/302 响应而不是跨主机自动跳转。
 * 若某个合法外联确实需要跟随 redirect，可使用 {@link RedirectFollower}
 * 工具手动 follow，每一跳都会重新走 host 校验。</p>
 */
@Slf4j
public final class SafeHttpClient {

    private SafeHttpClient() {}

    /**
     * 构建一个带 SSRF 防护的 RestTemplate。
     *
     * @param connectTimeoutMs 连接超时（毫秒）
     * @param readTimeoutMs    读超时（毫秒）
     */
    public static RestTemplate build(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod)
                    throws IOException {
                super.prepareConnection(connection, httpMethod);
                // 漏洞 23 修复：连接维度禁用自动 redirect，
                // 避免 302 被攻击者借来跳到私网（云元数据地址 / DNS 重绑定后的内网 IP）。
                // 本设置作用于每个连接实例，不影响 JVM 全局 HttpURLConnection.setFollowRedirects 的状态，
                // 因此其他依赖默认 redirect 的代码路径不受波及。
                connection.setInstanceFollowRedirects(false);
                String host = connection.getURL().getHost();
                assertHostNotPrivate(host);
            }
        };
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }

    /**
     * 解析 host 并对所有返回的 IP 调用 {@link PrivateNetworkValidator#isBlockedAddress(InetAddress)}。
     * 命中任意私网/本机/链路本地/云元数据地址即抛 {@link SSRFBlockedException}。
     */
    private static void assertHostNotPrivate(String host) {
        if (host == null || host.isBlank()) {
            throw new SSRFBlockedException("出站请求 host 缺失");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new SSRFBlockedException("出站请求无法解析 host: " + host);
        }
        for (InetAddress addr : addresses) {
            if (PrivateNetworkValidator.isBlockedAddress(addr)) {
                log.warn("SSRF blocked at connection stage: host={}, ip={}",
                        host, addr.getHostAddress());
                throw new SSRFBlockedException("禁止指向内网或本机地址");
            }
        }
    }
}
