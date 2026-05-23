package com.campusforum.common;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
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

    static final String GCM_PREFIX = "gcm:";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final byte[] GCM_KEY;
    // 旧硬编码 key 仅用于解密历史 AES-ECB 密文（懒迁移用）；不再用于新加密。
    private static final byte[] LEGACY_ECB_KEY = "CampusForum@1234".getBytes(StandardCharsets.UTF_8);

    static {
        // 优先读 system property（测试环境通过 -D 或 System.setProperty 注入），
        // 其次读 env var（生产 docker 注入）。
        // 缺失时在 dev / 非生产环境降级使用内置 fallback；生产环境（SPRING_PROFILES_ACTIVE=prod）仍 fail-hard。
        String configured = System.getProperty("app.crypto.key");
        if (configured == null || configured.isBlank()) {
            configured = System.getenv("APP_CRYPTO_KEY");
        }
        if (configured == null || configured.isBlank()) {
            String activeProfile = System.getProperty("spring.profiles.active");
            if (activeProfile == null || activeProfile.isBlank()) {
                activeProfile = System.getenv("SPRING_PROFILES_ACTIVE");
            }
            if (activeProfile != null && activeProfile.contains("prod")) {
                throw new IllegalStateException(
                        "APP_CRYPTO_KEY environment variable is required in production. "
                                + "Generate one with: openssl rand -base64 32");
            }
            // dev fallback：32 字节 base64，仅本地开发可用。
            configured = "ZGV2LW9ubHktY3J5cHRvdXRpbHMtZmFsbGJhY2stMzI=";
            System.err.println(
                    "[CryptoUtils] WARNING: APP_CRYPTO_KEY not set, using insecure dev fallback. "
                            + "This must be overridden in production via env var.");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(configured.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("APP_CRYPTO_KEY must be valid base64", e);
        }
        if (decoded.length != 32) {
            throw new IllegalStateException(
                    "APP_CRYPTO_KEY must decode to 32 bytes (AES-256), got " + decoded.length);
        }
        GCM_KEY = decoded;
    }

    public static String encrypt(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(GCM_KEY, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + cipherText.length);
            buf.put(iv);
            buf.put(cipherText);
            return GCM_PREFIX + Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    public static String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) return encrypted;
        if (encrypted.startsWith(GCM_PREFIX)) {
            return decryptGcm(encrypted.substring(GCM_PREFIX.length()));
        }
        return decryptLegacyEcb(encrypted);
    }

    /**
     * 判断密文是否为历史 AES-ECB 格式，调用方据此决定是否需要执行懒迁移。
     */
    public static boolean isLegacyFormat(String encrypted) {
        return encrypted != null && !encrypted.isBlank() && !encrypted.startsWith(GCM_PREFIX);
    }

    private static String decryptGcm(String base64Body) {
        try {
            byte[] data = Base64.getDecoder().decode(base64Body);
            if (data.length < GCM_IV_LENGTH) {
                throw new IllegalArgumentException("ciphertext too short");
            }
            ByteBuffer buf = ByteBuffer.wrap(data);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buf.get(iv);
            byte[] cipherText = new byte[buf.remaining()];
            buf.get(cipherText);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(GCM_KEY, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM decryption failed", e);
        }
    }

    private static String decryptLegacyEcb(String encrypted) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(LEGACY_ECB_KEY, "AES");
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encrypted));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 兼容历史上偶发的明文存储情形（旧实现 decrypt 失败时返回原字符串）。
            return encrypted;
        }
    }
}
