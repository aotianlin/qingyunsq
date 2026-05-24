package com.campusforum.infra.security.crypto;

import com.campusforum.infra.metrics.SecurityMetrics;
import com.campusforum.infra.security.CryptoException;
import com.campusforum.infra.security.SecurityProperties;
import com.campusforum.infra.security.crypto.legacy.LegacyEcbAccessor;
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
 * <p>提供 {@link #decryptLegacyEcb(String, long)} 用于解密旧 ECB 密文（通过
 * {@link LegacyEcbAccessor} 转发到包私有的 {@code EcbCryptoUtils}），
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

    /**
     * 旧 ECB 解密链路在调用方未提供 tenantId 时使用的占位值。
     *
     * <p>对应仅在 {@link #decryptLegacyEcb(String)} 兼容签名内部转发时使用，
     * 监控 tag 上标记为 0 表示"未明确租户"，便于运维识别尚未迁移到新签名的
     * 调用方并在 grafana 上单独排查。</p>
     */
    private static final long TENANT_UNSPECIFIED = 0L;

    private final SecretKeySpec masterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 安全监控埋点组件（T9.1）。旧 ECB 解密入口在每次进入 legacy 分支时累加
     * {@code crypto_decrypt_legacy_total} 计数，并在解密失败时累加
     * {@code crypto_decrypt_failed_total}，便于评估迁移完成度。
     */
    private final SecurityMetrics securityMetrics;

    public CryptoService(SecurityProperties props, SecurityMetrics securityMetrics) {
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
        this.securityMetrics = securityMetrics;
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
     * 兼容旧版 ECB 密文解密入口（带租户 tag 的主 API）。
     *
     * <p>对应 bugfix.md 漏洞 1 + 漏洞 32 的修复：</p>
     * <ul>
     *   <li>转发到包私有的 {@code EcbCryptoUtils}（通过 {@link LegacyEcbAccessor}
     *       公开桥接），业务代码不再能直接看到旧密钥实现；</li>
     *   <li>每次进入分支即累加 {@code crypto_decrypt_legacy_total{tenant_id=X}}
     *       计数，运维可按租户评估迁移完成度，连续 N 天为 0 即视为可清理；</li>
     *   <li>解密失败先累加 {@code crypto_decrypt_failed_total} 再抛出
     *       {@link CryptoException}，绝不再回退原始密文。</li>
     * </ul>
     *
     * @param ciphertext base64 编码的旧 ECB 密文
     * @param tenantId   触发解密的租户 ID（用于 metrics tag 分桶）
     * @return 解密后的明文
     * @throws CryptoException 解密失败
     */
    public String decryptLegacyEcb(String ciphertext, long tenantId) {
        // 进入 legacy 分支即埋点，无论后续成功或失败都会被记录
        securityMetrics.cryptoDecryptLegacy(tenantId);
        try {
            return LegacyEcbAccessor.decrypt(ciphertext);
        } catch (CryptoException e) {
            // 失败统计独立 Counter，运维可基于失败率识别"密钥配置异常 / 历史
            // 数据格式损坏 / 攻击者投毒"等异常情况
            securityMetrics.cryptoDecryptFailed();
            throw e;
        }
    }

    /**
     * 兼容旧签名：未携带 tenantId 的解密入口。
     *
     * <p>本方法仅供尚未迁移到新签名的历史调用方临时使用，内部以
     * {@link #TENANT_UNSPECIFIED}（0）作为占位 tag 转发到主 API，
     * 并在每次调用时打印 WARN 日志提示调用方升级。</p>
     *
     * <p>新代码应直接调用 {@link #decryptLegacyEcb(String, long)} 并显式
     * 传入 tenantId，便于运维在 grafana 上按租户维度评估迁移进度。</p>
     *
     * @deprecated 请改用 {@link #decryptLegacyEcb(String, long)} 并显式传入租户 ID
     */
    @Deprecated(forRemoval = true)
    public String decryptLegacyEcb(String ciphertext) {
        log.warn("decryptLegacyEcb 旧签名被调用：调用方未提供 tenantId，请尽快迁移到带 tenantId 的新签名");
        return decryptLegacyEcb(ciphertext, TENANT_UNSPECIFIED);
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
