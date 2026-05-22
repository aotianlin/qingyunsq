package com.campusforum.infra.security.crypto;

import com.campusforum.common.CryptoUtils;
import com.campusforum.infra.security.CryptoException;
import com.campusforum.infra.security.SecurityProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 对称加密服务：AES-GCM-256 + HKDF-SHA256 派生子密钥。
 *
 * <p>主密钥从配置 {@code security.crypto.master-key} 注入（生产由 ENV {@code CRYPTO_MASTER_KEY}），
 * 长度需 ≥ 32 字节。每次加密派生针对特定 purpose 的子密钥，再生成 12 字节随机 IV，
 * 输出格式为 {@code base64(IV || GCM_CIPHERTEXT_WITH_TAG)}。</p>
 *
 * <p>解密失败一律抛 {@link CryptoException}，绝不回退原文，避免攻击者借此探测明文残留。</p>
 *
 * <p>提供 {@link #decryptLegacyEcb(String)} 用于解密旧 {@link CryptoUtils} ECB 密文，
 * 仅在租户 AI 配置灰度迁移阶段调用，迁移完成后该方法将被删除。</p>
 */
@Slf4j
@Component
public class CryptoService {

    /** GCM 模式标准算法名。 */
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";

    /** GCM 推荐 IV 长度为 12 字节（96 bit）。 */
    private static final int IV_LENGTH = 12;

    /** GCM 认证 tag 长度（128 bit），与默认实现一致。 */
    private static final int TAG_BITS = 128;

    /** AES-256 子密钥长度（字节）。 */
    private static final int SUB_KEY_LENGTH = 32;

    /** HKDF salt 固定值，用于实现密钥分域；不同 purpose 派生不同子密钥。 */
    private static final byte[] HKDF_SALT = "campusforum-hkdf-salt".getBytes(StandardCharsets.UTF_8);

    private final SecretKeySpec masterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public CryptoService(SecurityProperties props) {
        String key = props.getCrypto().getMasterKey();
        if (key == null || key.isBlank()) {
            // SecurityStartupValidator 已经做了拦截，这里再防御一次以防有人直接 new
            throw new IllegalStateException("security.crypto.master-key 未配置");
        }
        byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("security.crypto.master-key 长度不足 32 字节");
        }
        // 仅取前 32 字节作为 AES-256 主密钥；HKDF 派生时使用全量字节作为 IKM
        this.masterKey = new SecretKeySpec(Arrays.copyOf(bytes, SUB_KEY_LENGTH), "AES");
    }

    /**
     * 加密明文。
     *
     * @param plaintext 明文（不能为 null/空）
     * @param purpose   用途标识，用作 HKDF info 实现密钥分域，例如 "tenant-ai-key"
     * @return base64 编码的密文（IV || ciphertext || tag）
     */
    public String encrypt(String plaintext, String purpose) {
        if (plaintext == null) {
            throw new CryptoException("加密明文不能为 null");
        }
        try {
            SecretKeySpec subKey = deriveSubKey(purpose);
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, subKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            // 不在异常 message 中携带明文 / 密钥信息
            log.error("AES-GCM encrypt failed: {}", e.getClass().getSimpleName());
            throw new CryptoException("加密失败");
        }
    }

    /**
     * 解密密文。
     *
     * @param ciphertext base64 编码的密文
     * @param purpose    必须与加密时一致，否则解密失败
     * @return 明文
     * @throws CryptoException 解密失败（密钥不匹配、密文被篡改、格式非法等）
     */
    public String decrypt(String ciphertext, String purpose) {
        if (ciphertext == null || ciphertext.isBlank()) {
            throw new CryptoException("密文不能为空");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            if (combined.length <= IV_LENGTH) {
                throw new CryptoException("密文长度非法");
            }
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            byte[] cipherText = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);

            SecretKeySpec subKey = deriveSubKey(purpose);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, subKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(cipherText);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (CryptoException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            // base64 解码失败
            throw new CryptoException("密文格式非法");
        } catch (GeneralSecurityException e) {
            log.error("AES-GCM decrypt failed: {}", e.getClass().getSimpleName());
            throw new CryptoException("解密失败");
        }
    }

    /**
     * 兼容旧版 ECB 密文解密入口。仅供历史数据灰度迁移使用，迁移完成后删除。
     */
    public String decryptLegacyEcb(String ciphertext) {
        try {
            return CryptoUtils.decrypt(ciphertext);
        } catch (Exception e) {
            log.error("Legacy ECB decrypt failed: {}", e.getClass().getSimpleName());
            throw new CryptoException("旧密文解密失败");
        }
    }

    /**
     * HKDF-SHA256 派生子密钥。
     *
     * <p>简化版实现，等价于 RFC 5869 的 expand 阶段，对单次输出 32 字节足够。
     * extract 阶段直接使用主密钥作为 PRK，salt 固定，info 为 purpose。</p>
     */
    private SecretKeySpec deriveSubKey(String purpose) throws GeneralSecurityException {
        if (purpose == null || purpose.isBlank()) {
            throw new IllegalArgumentException("purpose 不能为空");
        }
        // HKDF-Extract: PRK = HMAC-SHA256(salt, IKM=masterKey)
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(HKDF_SALT, "HmacSHA256"));
        byte[] prk = mac.doFinal(masterKey.getEncoded());

        // HKDF-Expand: T(1) = HMAC-SHA256(PRK, info || 0x01)
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        byte[] info = purpose.getBytes(StandardCharsets.UTF_8);
        byte[] input = new byte[info.length + 1];
        System.arraycopy(info, 0, input, 0, info.length);
        input[info.length] = 0x01;
        byte[] okm = mac.doFinal(input);

        // 取前 32 字节作为 AES-256 子密钥
        return new SecretKeySpec(Arrays.copyOf(okm, SUB_KEY_LENGTH), "AES");
    }
}
