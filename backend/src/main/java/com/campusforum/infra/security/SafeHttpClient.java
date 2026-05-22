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
