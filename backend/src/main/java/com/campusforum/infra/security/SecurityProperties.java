package com.campusforum.infra.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 平台安全相关的可配置项。
 *
 * <p>这些参数集中在 {@code security} 命名空间下，便于运维通过环境变量在不重启代码的情况下调整。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {

    /** 登录失败锁定相关配置。 */
    private LoginLockout loginLockout = new LoginLockout();

    /** 可信反向代理 IP 列表（CIDR 或单 IP）。命中时才解析 X-Forwarded-For/X-Real-IP。 */
    private List<String> trustedProxies = new ArrayList<>(List.of(
            "127.0.0.1", "::1",
            "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"
    ));

    /** 资源签名 URL HMAC 密钥，必须在生产覆盖。 */
    private String signedUrlSecret = "campus-forum-default-signed-url-secret-please-override";

    /** 资源签名 URL 默认有效期（秒），下载/预览短期一次性使用足以。 */
    private int signedUrlTtlSeconds = 60;

    /** AI baseUrl 是否禁止指向私网/本机地址，避免 SSRF。生产保持 true。 */
    private boolean aiBaseUrlBlockPrivateNetwork = true;

    @Data
    public static class LoginLockout {
        /** 是否启用失败锁定。 */
        private boolean enabled = true;

        /** 触发锁定的连续失败次数。 */
        private int maxFailures = 5;

        /** 失败计数滑动窗口（秒）。 */
        private int windowSeconds = 900;

        /** 锁定持续时间（秒）。 */
        private int lockoutSeconds = 900;
    }
}
