package com.campusforum.infra.security;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

/**
 * 防 SSRF 工具：禁止 baseUrl 指向私网/本机/链路本地地址。
 *
 * <p>租户 AI 配置由 SUPER_ADMIN 维护，但仍需阻挡"指向内网 ES/Redis/云元数据接口"这种外部投毒，
 * 因此在保存配置或调用前一律走这里。</p>
 */
@Slf4j
public final class PrivateNetworkValidator {

    private PrivateNetworkValidator() {}

    /**
     * 校验给定 URL 是否安全（公网 https）。失败抛 IllegalArgumentException。
     *
     * @param url       需要校验的 URL
     * @param requireHttps 是否强制 https，true 时拒绝 http
     */
    public static void requirePublic(String url, boolean requireHttps) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL 不能为空");
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("URL 非法");
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("URL 必须包含协议（http/https）");
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("仅支持 http/https 协议");
        }
        if (requireHttps && !"https".equals(scheme)) {
            throw new IllegalArgumentException("仅允许 https 地址");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL 主机名缺失");
        }
        // 直接拒绝最常见的本机/私网形式，避免依赖 DNS 查询导致漏掉
        String lower = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(lower)
                || lower.endsWith(".localhost")
                || lower.endsWith(".local")
                || lower.endsWith(".internal")) {
            throw new IllegalArgumentException("禁止使用本机或内网域名");
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses.length == 0) {
                throw new IllegalArgumentException("无法解析主机");
            }
            for (InetAddress addr : addresses) {
                if (isBlocked(addr)) {
                    throw new IllegalArgumentException("禁止指向内网/本机/链路本地地址");
                }
            }
        } catch (java.net.UnknownHostException e) {
            // 解析失败时按拒绝处理，避免离线绕过
            throw new IllegalArgumentException("无法解析主机：" + host);
        }
    }

    private static boolean isBlocked(InetAddress addr) {
        return isBlockedAddress(addr);
    }

    /**
     * 公开 API：判断给定 InetAddress 是否属于"禁止外联"范畴。
     *
     * <p>由 {@link com.campusforum.infra.security.SafeHttpClient} 在 Socket 连接阶段二次调用，
     * 防御 DNS 重绑定（校验阶段返回公网 IP，连接阶段返回内网 IP）攻击。</p>
     */
    public static boolean isBlockedAddress(InetAddress addr) {
        if (addr == null) return true;
        return addr.isLoopbackAddress()
                || addr.isAnyLocalAddress()
                || addr.isLinkLocalAddress()
                || addr.isSiteLocalAddress()      // 10/8、172.16/12、192.168/16
                || addr.isMulticastAddress()
                || isCloudMetadata(addr);
    }

    /** 阻止常见云元数据地址。 */
    private static boolean isCloudMetadata(InetAddress addr) {
        String host = addr.getHostAddress();
        // AWS / GCP / Azure / 阿里云元数据接口都使用 169.254.169.254，已被 link-local 覆盖；这里冗余兜底。
        return "169.254.169.254".equals(host) || "100.100.100.200".equals(host);
    }
}
