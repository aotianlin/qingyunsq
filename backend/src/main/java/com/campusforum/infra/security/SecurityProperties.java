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

    /** 对称加密相关配置（AES-GCM 主密钥及兼容旗标）。 */
    private Crypto crypto = new Crypto();

    /** CORS 跨域配置，生产必须由运维通过环境变量显式覆盖。 */
    private Cors cors = new Cors();

    /** 文件上传相关安全配置。 */
    private Upload upload = new Upload();

    /** WebSocket 一次性票据配置。 */
    private WsTicket wsTicket = new WsTicket();

    @Data
    public static class LoginLockout {
        /** 是否启用失败锁定。 */
        private boolean enabled = true;

        /** 触发账号锁定的连续失败次数。 */
        private int maxFailures = 10;

        /** 失败计数滑动窗口（秒）。 */
        private int windowSeconds = 900;

        /** 账号锁定持续时间（秒），缩短为 5 分钟以减少针对性 DoS 影响。 */
        private int lockoutSeconds = 300;

        /** IP 维度触发锁定的连续失败次数（与账号维度独立）。 */
        private int ipMaxFailures = 20;

        /** IP 维度锁定持续时间（秒）。 */
        private int ipLockoutSeconds = 900;
    }

    @Data
    public static class Crypto {
        /** AES-GCM 主密钥（生产从 ENV CRYPTO_MASTER_KEY 注入），长度需 ≥ 32 字节。 */
        private String masterKey;

        /** 紧急回滚开关：true 时跳过新加密路径，沿用旧 ECB 实现。 */
        private boolean legacyMode = false;
    }

    @Data
    public static class Cors {
        /** 允许的跨域来源列表，生产环境必须显式配置前端域名。 */
        private List<String> allowedOrigins = new ArrayList<>();

        /** 允许的请求方法。 */
        private List<String> allowedMethods = new ArrayList<>(List.of(
                "GET", "POST", "PUT", "DELETE", "OPTIONS"));
    }

    @Data
    public static class Upload {
        /** 是否使用 Apache Tika 检测真实 MIME 类型。 */
        private boolean realMimeCheck = true;

        /** 全局禁止上传的扩展名（在 allowed-extensions 之上的二次过滤）。 */
        private List<String> blockedExtensions = new ArrayList<>();

        /** 头像 / 封面 URL 允许的域名白名单（用于反 XSS 与 Open Redirect）。 */
        private List<String> allowedAssetHosts = new ArrayList<>();
    }

    @Data
    public static class WsTicket {
        /** WebSocket 票据有效期（秒），短即可，仅用于建立 WS 连接。 */
        private int ttlSeconds = 30;

        /** true 时拒绝旧 token 走 query string，强制使用 ticket。 */
        private boolean enforced = false;
    }
}
