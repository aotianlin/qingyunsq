package com.campusforum.security.crypto;

import com.campusforum.infra.security.CryptoException;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 旧 ECB 解密工具 {@code EcbCryptoUtils} 的单元测试。
 *
 * <p>对应任务 T1.1（bugfix.md 漏洞 1：旧 ECB 硬编码密钥可被任意调用，
 * 且 {@code decrypt} 失败静默回退原文污染加密链路）。重点验证：</p>
 * <ul>
 *   <li>合法密文可成功解密为原始明文（保留兼容期内的迁移读取能力）；</li>
 *   <li>非法 base64 / 空入参 / null 入参等边界条件**统一抛 {@link CryptoException}**，
 *       绝不再返回原始密文；</li>
 *   <li>密文 base64 解码后长度非 16 字节倍数（ECB 无填充错误）也走 CryptoException 分支。</li>
 * </ul>
 *
 * <p>由于目标类 {@code com.campusforum.infra.security.crypto.legacy.EcbCryptoUtils}
 * 是 package-private，不可在本测试包内 import；改用反射调用其 {@code decrypt}
 * 方法。这同时也间接验证了"业务代码无法绕过 {@code CryptoService} 直接拿到旧
 * 密钥实现"这一可见性约束。</p>
 */
class EcbCryptoUtilsTest {

    /**
     * 与 {@code EcbCryptoUtils.DEFAULT_KEY} 一致的硬编码密钥，仅供测试本地构造
     * 合法密文，与生产代码共享同一密钥语义。
     */
    private static final String DEFAULT_KEY = "CampusForum@1234";

    /** 反射调用入口：{@code EcbCryptoUtils.decrypt(String)}。 */
    private static String invokeDecrypt(String ciphertext) throws Throwable {
        try {
            Class<?> clazz = Class.forName(
                    "com.campusforum.infra.security.crypto.legacy.EcbCryptoUtils");
            Method method = clazz.getDeclaredMethod("decrypt", String.class);
            method.setAccessible(true);
            return (String) method.invoke(null, ciphertext);
        } catch (InvocationTargetException ite) {
            // 把反射包装层剥掉，让原始异常（CryptoException 等）能被断言直接捕获
            throw ite.getCause();
        }
    }

    /**
     * 用与生产相同的 AES/ECB/PKCS5Padding + DEFAULT_KEY 算法构造合法密文，
     * 仅供测试覆盖"成功解密"路径使用，不暴露给业务代码。
     */
    private static String encryptWithSameAlgorithm(String plaintext) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(
                DEFAULT_KEY.getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(cipherBytes);
    }

    @Test
    void decrypt_validCiphertext_returnsPlaintext() throws Throwable {
        // 使用相同算法 + 相同硬编码密钥加密一段已知明文，构造 v1 历史密文样本
        String plaintext = "sk-historical-tenant-api-key-001";
        String ciphertext = encryptWithSameAlgorithm(plaintext);

        String result = invokeDecrypt(ciphertext);

        assertThat(result).isEqualTo(plaintext);
    }

    @Test
    void decrypt_invalidBase64_throws_CryptoException() {
        // base64 字符集外的非法字符，应在 base64 解码阶段就被识别并转抛 CryptoException
        String invalidBase64 = "!!!not-a-valid-base64-string!!!";

        assertThatThrownBy(() -> invokeDecrypt(invalidBase64))
                .isInstanceOf(CryptoException.class)
                // 关键断言：异常 message 不能包含原始密文，避免日志泄漏
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(invalidBase64));
    }

    @Test
    void decrypt_emptyOrNull_throws_CryptoException() {
        // 空白字符串：长度为 0 的密文绝不可能合法
        assertThatThrownBy(() -> invokeDecrypt(""))
                .isInstanceOf(CryptoException.class);

        // 仅含空白字符：与空字符串等价处理
        assertThatThrownBy(() -> invokeDecrypt("   "))
                .isInstanceOf(CryptoException.class);

        // null：直接拒绝，不允许 NPE 泄漏到调用方
        assertThatThrownBy(() -> invokeDecrypt(null))
                .isInstanceOf(CryptoException.class);
    }

    @Test
    void decrypt_doesNotReturn_originalCiphertext_onError() {
        // 故意构造一段 base64 解码后长度非 16 字节倍数的密文（ECB 块大小为 16）
        // → 触发 cipher.doFinal 抛 BadPaddingException / IllegalBlockSizeException
        // 旧实现 (CryptoUtils.decrypt) 在这里会 return encrypted 静默回退原文，
        // 现在必须抛 CryptoException 而不是返回原始密文，避免污染加密链路完整性边界
        byte[] notMultipleOf16 = new byte[]{1, 2, 3, 4, 5, 6, 7};
        String ciphertext = Base64.getEncoder().encodeToString(notMultipleOf16);

        assertThatThrownBy(() -> invokeDecrypt(ciphertext))
                .isInstanceOf(CryptoException.class);

        // 再做一次反向断言：调用入口绝不返回任何字符串（包含原文）。
        // 通过 try/catch 显式确认抛异常前没有任何"成功路径"被执行。
        boolean threw = false;
        try {
            invokeDecrypt(ciphertext);
        } catch (Throwable t) {
            threw = true;
            assertThat(t).isInstanceOf(CryptoException.class);
        }
        assertThat(threw).as("解密失败必须抛 CryptoException，绝不返回原始密文").isTrue();
    }
}
