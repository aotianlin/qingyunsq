package com.campusforum.common;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CryptoUtils 单元测试 — 验证 AES-GCM 实现 + 旧 ECB 解密兼容 + 格式识别。
 *
 * 注：CryptoUtils 静态块要求 app.crypto.key system property 或 APP_CRYPTO_KEY env var。
 * 由 maven-surefire-plugin 的 systemPropertyVariables 全局注入（pom.xml）。
 */
class CryptoUtilsTest {

    @Test
    void encrypt_decrypt_shouldRoundtrip() {
        String plain = "sk-9f6121c4089d4bfc88f56d0f540af139";
        String encrypted = CryptoUtils.encrypt(plain);
        assertThat(encrypted).startsWith(CryptoUtils.GCM_PREFIX);
        assertThat(CryptoUtils.decrypt(encrypted)).isEqualTo(plain);
    }

    @Test
    void encrypt_shouldProduceDifferentCiphertextForSameInput() {
        // AES-GCM 的核心改进：随机 IV → 同一明文产生不同密文。
        // 这是相对 AES-ECB（同明文必同密文）最重要的安全提升。
        String plain = "duplicate-input";
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            seen.add(CryptoUtils.encrypt(plain));
        }
        assertThat(seen).hasSize(10);
    }

    @Test
    void encrypt_shouldReturnAsIsForBlank() {
        assertThat(CryptoUtils.encrypt(null)).isNull();
        assertThat(CryptoUtils.encrypt("")).isEmpty();
    }

    @Test
    void decrypt_shouldHandleLegacyEcbFormat() throws Exception {
        // 用旧硬编码 key 构造一段 ECB 密文，验证 decrypt 能识别并兼容。
        String plain = "legacy-secret-value";
        SecretKeySpec keySpec = new SecretKeySpec(
                "CampusForum@1234".getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] cipherBytes = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        String legacyEncrypted = Base64.getEncoder().encodeToString(cipherBytes);

        // 不带 gcm: 前缀 → 走 legacy path
        assertThat(CryptoUtils.isLegacyFormat(legacyEncrypted)).isTrue();
        assertThat(CryptoUtils.decrypt(legacyEncrypted)).isEqualTo(plain);
    }

    @Test
    void isLegacyFormat_shouldDetectGcmPrefix() {
        assertThat(CryptoUtils.isLegacyFormat(null)).isFalse();
        assertThat(CryptoUtils.isLegacyFormat("")).isFalse();
        assertThat(CryptoUtils.isLegacyFormat("gcm:something")).isFalse();
        assertThat(CryptoUtils.isLegacyFormat("ABC123base64==")).isTrue();
    }

    @Test
    void decrypt_shouldRejectTamperedGcmCiphertext() {
        String plain = "tamper-test";
        String encrypted = CryptoUtils.encrypt(plain);
        // 翻转密文中段一个字节 → GCM tag 校验失败 → 抛 IllegalStateException
        char[] chars = encrypted.toCharArray();
        int midIdx = encrypted.length() / 2;
        chars[midIdx] = chars[midIdx] == 'A' ? 'B' : 'A';
        String tampered = new String(chars);

        Throwable thrown = null;
        try {
            CryptoUtils.decrypt(tampered);
        } catch (Throwable t) {
            thrown = t;
        }
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
    }
}
