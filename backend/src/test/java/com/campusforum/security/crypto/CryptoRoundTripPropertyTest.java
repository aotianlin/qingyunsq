package com.campusforum.security.crypto;

import com.campusforum.infra.metrics.SecurityMetrics;
import com.campusforum.infra.security.CryptoException;
import com.campusforum.infra.security.SecurityProperties;
import com.campusforum.infra.security.crypto.CryptoService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 任务 TPBT.5（部分）/ design.md Property 5：加密互逆 + 跨 purpose 隔离。
 *
 * <p>验证两条核心安全属性：</p>
 * <ol>
 *   <li><b>互逆性</b>：对任意明文 {@code p} 与 purpose，{@code decrypt(encrypt(p, purpose), purpose) == p}。</li>
 *   <li><b>跨 purpose 隔离</b>：用 purposeA 加密的密文不能用 purposeB 解出，
 *       必须抛 {@link CryptoException}（HKDF 派生子密钥保证跨 purpose 之间互相独立）。</li>
 * </ol>
 *
 * <p>本测试纯单元，不连任何外部资源；用 {@link SimpleMeterRegistry} 提供
 * {@link SecurityMetrics} 依赖。属性测试运行 100 次随机用例。</p>
 */
class CryptoRoundTripPropertyTest {

    private static final String MASTER_KEY = "test-master-key-must-be-32-bytes-or-more-aaaaaaaa";

    private CryptoService buildService() {
        SecurityProperties props = new SecurityProperties();
        props.getCrypto().setMasterKey(MASTER_KEY);
        SecurityMetrics metrics = new SecurityMetrics(new SimpleMeterRegistry());
        return new CryptoService(props, metrics);
    }

    /**
     * 生成器：可打印 ASCII 文本（含中文 + 空白），长度 0..200。
     *
     * <p>jqwik 默认 {@code @ForAll String} 也可用，但限定字符集后可在更短时间内
     * 触发更多边界（含中文 / 空白 / 控制字符过滤），同时避免生成超长串拖慢测试。</p>
     */
    @Provide
    Arbitrary<String> texts() {
        return Arbitraries.strings()
                .ofMinLength(0)
                .ofMaxLength(200)
                .alpha().numeric()
                .withChars(' ', '\t', '\n', '中', '文', '!', '@', '#', '%', '&', '*', '?');
    }

    @Provide
    Arbitrary<String> purposes() {
        return Arbitraries.of("tenant-ai-key", "user-pii", "session-meta", "test-bucket");
    }

    @Property(tries = 100)
    void encrypt_thenDecrypt_returnsOriginal(@ForAll("texts") String plaintext,
                                             @ForAll("purposes") String purpose) {
        if (plaintext.isEmpty()) {
            // 空串走"加密明文不能为 null"分支不在本属性范围；过滤掉即可
            return;
        }
        CryptoService svc = buildService();
        String cipher = svc.encrypt(plaintext, purpose);
        String decrypted = svc.decrypt(cipher, purpose);
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Property(tries = 50)
    void encrypt_isNonDeterministic(@ForAll("texts") String plaintext,
                                    @ForAll("purposes") String purpose) {
        // GCM 用随机 IV，相同明文 + 相同 purpose 两次加密结果不同
        if (plaintext.isEmpty()) return;
        CryptoService svc = buildService();
        String c1 = svc.encrypt(plaintext, purpose);
        String c2 = svc.encrypt(plaintext, purpose);
        assertThat(c1).as("GCM 加密必须使用随机 IV，每次密文应不同").isNotEqualTo(c2);
        // 但都必须能解出来
        assertThat(svc.decrypt(c1, purpose)).isEqualTo(plaintext);
        assertThat(svc.decrypt(c2, purpose)).isEqualTo(plaintext);
    }

    @Test
    void crossPurpose_decryption_throws() {
        // 跨 purpose 解密应抛 CryptoException（HKDF 派生子密钥隔离）
        CryptoService svc = buildService();
        String cipher = svc.encrypt("super-secret", "tenant-ai-key");
        assertThatThrownBy(() -> svc.decrypt(cipher, "user-pii"))
                .isInstanceOf(CryptoException.class);
    }

    @Test
    void tampered_ciphertext_throws() {
        // 篡改密文（GCM 标签校验失败）必须抛错，而不是返回原密文 / 静默返回 null
        CryptoService svc = buildService();
        String cipher = svc.encrypt("plain", "tenant-ai-key");
        // 把最后一个 base64 字符替换为不同的值
        char last = cipher.charAt(cipher.length() - 1);
        char tamperedChar = last == 'A' ? 'B' : 'A';
        String tampered = cipher.substring(0, cipher.length() - 1) + tamperedChar;
        assertThatThrownBy(() -> svc.decrypt(tampered, "tenant-ai-key"))
                .isInstanceOf(CryptoException.class);
    }
}
