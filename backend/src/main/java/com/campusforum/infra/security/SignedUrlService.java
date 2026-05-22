package com.campusforum.infra.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 资源访问短期签名工具：用 HMAC-SHA256 对 (用户ID + 资源类型 + 资源ID + 操作 + 过期时间戳) 签名。
 *
 * <p>调用方在发起下载/预览前先调一次"取签名"接口，拿到 token 后直接拼到 URL，
 * 服务端校验通过即可放行。这样：</p>
 * <ul>
 *   <li>token 自带过期，防止被三方/日志拷走永久使用；</li>
 *   <li>token 与 user/resource 绑定，泄漏后他人也无法借用；</li>
 *   <li>真正的会话 token 不再放进 query string。</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class SignedUrlService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String DELIMITER = "|";

    private final SecurityProperties properties;

    /**
     * 生成 token。格式：base64url(payload).base64url(signature)，payload = "userId|type|id|action|exp"
     */
    public String sign(long userId, String type, long resourceId, String action, long expiresAtSeconds) {
        String payload = userId + DELIMITER + type + DELIMITER + resourceId
                + DELIMITER + action + DELIMITER + expiresAtSeconds;
        String mac = hmac(payload);
        return urlEncode(payload) + "." + urlEncode(mac);
    }

    /**
     * 简化签名：使用配置的默认 TTL，返回完整 token 与过期时间。
     */
    public SignedToken sign(long userId, String type, long resourceId, String action) {
        long exp = System.currentTimeMillis() / 1000 + properties.getSignedUrlTtlSeconds();
        return new SignedToken(sign(userId, type, resourceId, action, exp), exp);
    }

    /**
     * 校验 token：成功返回 payload，失败返回 null。
     */
    public Verified verify(String token, String expectedType, long expectedResourceId, String expectedAction) {
        if (token == null || token.isBlank() || !token.contains(".")) return null;
        String[] parts = token.split("\\.");
        if (parts.length != 2) return null;
        String payload;
        String signature;
        try {
            payload = new String(urlDecode(parts[0]), StandardCharsets.UTF_8);
            signature = new String(urlDecode(parts[1]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
        String expected = hmac(payload);
        // constant-time 比较，避免时序信息泄漏
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        String[] fields = payload.split("\\|");
        if (fields.length != 5) return null;
        try {
            long userId = Long.parseLong(fields[0]);
            String type = fields[1];
            long resourceId = Long.parseLong(fields[2]);
            String action = fields[3];
            long exp = Long.parseLong(fields[4]);
            if (!type.equals(expectedType) || resourceId != expectedResourceId || !action.equals(expectedAction)) {
                return null;
            }
            if (System.currentTimeMillis() / 1000 > exp) return null;
            return new Verified(userId, type, resourceId, action, exp);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(properties.getSignedUrlSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] result = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().withoutPadding().encodeToString(result);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC compute failed", e);
        }
    }

    private static String urlEncode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] urlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    public record SignedToken(String token, long expiresAtSeconds) {}

    public record Verified(long userId, String type, long resourceId, String action, long expiresAtSeconds) {}
}
