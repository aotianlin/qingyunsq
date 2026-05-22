package com.campusforum.common;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * @deprecated 已被 {@link com.campusforum.infra.security.crypto.CryptoService}（AES-GCM + HKDF）替代。
 * 本类仅保留 {@link #decrypt(String)} 方法用于解密历史 ECB 密文以支持灰度迁移，
 * 在所有租户的 ai_config 都升级到 encVersion=2 之后将彻底删除。
 *
 * <p><b>请勿在新代码中调用本类。</b></p>
 */
@Deprecated(forRemoval = true)
public class CryptoUtils {

    // 默认的 16 字节密钥（生产环境应配置到外部）
    private static final String DEFAULT_KEY = "CampusForum@1234";

    public static String encrypt(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        try {
            SecretKeySpec keySpec = new SecretKeySpec(DEFAULT_KEY.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            return raw;
        }
    }

    public static String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) return encrypted;
        try {
            SecretKeySpec keySpec = new SecretKeySpec(DEFAULT_KEY.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encrypted));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 如果解密失败（例如旧版是明文存储），则直接返回原字符串
            return encrypted;
        }
    }
}
