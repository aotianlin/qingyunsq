package com.campusforum.security.crypto;

import com.campusforum.infra.metrics.SecurityMetrics;
import com.campusforum.infra.security.CryptoException;
import com.campusforum.infra.security.SecurityProperties;
import com.campusforum.infra.security.crypto.CryptoService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CryptoService#decryptLegacyEcb(String, long)} 监控埋点测试。
 *
 * <p>对应任务 T1.6（bugfix.md 漏洞 1 + 漏洞 32）：每次进入 legacy 分支必须
 * 累加 {@code crypto_decrypt_legacy_total{tenant_id=X}}；解密失败必须额外
 * 累加 {@code crypto_decrypt_failed_total} 并抛 {@link CryptoException}，
 * 绝不再回退原始密文。</p>
 *
 * <p>本测试不启动 Spring 上下文，直接用 {@link SimpleMeterRegistry} 内存级
 * 注册表配合真实 {@code SecurityMetrics} 与真实 {@code EcbCryptoUtils}，
 * 既覆盖 metrics 行为，又顺便回归"成功解密"链路。</p>
 */
class CryptoServiceLegacyMetricsTest {

    /** 与 {@code EcbCryptoUtils.DEFAULT_KEY} 一致的硬编码密钥，仅供测试构造合法密文。 */
    private static final String DEFAULT_KEY = "CampusForum@1234";

    /**
     * AES-GCM 主密钥（≥ 32 字节，仅供测试），构造 {@link CryptoService} 时
     * 会校验长度，随便给一段固定字符串即可。
     */
    private static final String TEST_MASTER_KEY = "test-master-key-32-bytes-padding!!";

    private MeterRegistry registry;
    private SecurityMetrics securityMetrics;
    private CryptoService cryptoService;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        securityMetrics = new SecurityMetrics(registry);

        SecurityProperties props = new SecurityProperties();
        props.getCrypto().setMasterKey(TEST_MASTER_KEY);
        cryptoService = new CryptoService(props, securityMetrics);
    }

    /** 用旧 ECB 算法构造一段合法密文，用于测试成功路径。 */
    private static String encryptWithLegacyEcb(String plaintext) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(
                DEFAULT_KEY.getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(cipherBytes);
    }

    @Test
    void legacyDecrypt_increments_counter_with_tenant_tag() throws Exception {
        // 准备：构造一段合法的旧 ECB 密文
        String plaintext = "sk-legacy-tenant-key";
        String ciphertext = encryptWithLegacyEcb(plaintext);
        long tenantId = 42L;

        // 执行：调用一次新签名（带 tenantId）
        String result = cryptoService.decryptLegacyEcb(ciphertext, tenantId);

        // 断言 1：解密结果正确，回归 v1 ECB 兼容期内的迁移读取能力
        assertThat(result).isEqualTo(plaintext);

        // 断言 2：crypto_decrypt_legacy_total{tenant_id=42} 被累加 1 次
        Counter counter = registry.find("crypto_decrypt_legacy")
                .tag("tenant_id", String.valueOf(tenantId))
                .counter();
        assertThat(counter)
                .as("crypto_decrypt_legacy 计数器必须以 tenant_id=42 标签存在")
                .isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);

        // 断言 3：成功路径不应触发失败 Counter
        Counter failed = registry.find("crypto_decrypt_failed").counter();
        if (failed != null) {
            assertThat(failed.count()).isEqualTo(0.0);
        }
    }

    @Test
    void legacyDecrypt_onFailure_increments_failed_counter_and_throws() {
        // 准备：故意构造非法密文（base64 字符集外字符）
        String invalid = "not-base64-!!!";
        long tenantId = 7L;

        // 执行：必须抛 CryptoException，绝不返回原始密文
        assertThatThrownBy(() -> cryptoService.decryptLegacyEcb(invalid, tenantId))
                .isInstanceOf(CryptoException.class);

        // 断言 1：legacy 计数器仍按 tenant_id=7 累加（即便失败也代表"曾经路过 legacy 分支"）
        Counter legacy = registry.find("crypto_decrypt_legacy")
                .tag("tenant_id", String.valueOf(tenantId))
                .counter();
        assertThat(legacy).isNotNull();
        assertThat(legacy.count()).isEqualTo(1.0);

        // 断言 2：失败 Counter 被累加，方便运维监控异常密钥 / 投毒事件
        Counter failed = registry.find("crypto_decrypt_failed").counter();
        assertThat(failed)
                .as("crypto_decrypt_failed 计数器必须存在")
                .isNotNull();
        assertThat(failed.count()).isEqualTo(1.0);
    }

    @Test
    void deprecatedSingleArg_overload_forwards_with_tenant_zero() throws Exception {
        // 准备：构造合法密文以便走成功分支，验证旧签名仍能工作（保留兼容期）
        String plaintext = "legacy-call-without-tenant";
        String ciphertext = encryptWithLegacyEcb(plaintext);

        // 执行：调用旧的单参签名（已 @Deprecated）
        @SuppressWarnings("deprecation")
        String result = cryptoService.decryptLegacyEcb(ciphertext);

        assertThat(result).isEqualTo(plaintext);

        // 断言：tenant_id=0（占位）维度的计数被累加，便于 grafana 上单独排查
        // "尚未迁移到新签名的调用方"
        Counter counter = registry.find("crypto_decrypt_legacy")
                .tag("tenant_id", "0")
                .counter();
        assertThat(counter)
                .as("旧签名应以 tenant_id=0 占位累加，便于运维识别未迁移调用方")
                .isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
}
