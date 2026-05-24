package com.campusforum.security.metrics;

import com.campusforum.infra.metrics.SecurityMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SecurityMetrics 单元测试。
 *
 * <p>验证集中埋点组件能正确累加各类安全 Counter 指标，并按设计携带
 * 业务 tag（tenant_id / stage / ext / detected / reason / route / action）。</p>
 *
 * <p>对应任务：T9.1（bugfix.md 漏洞 32：监控埋点缺失）。</p>
 */
class SecurityMetricsTest {

    /** 使用 Micrometer 提供的 SimpleMeterRegistry 做内存级断言，无需 Spring 上下文。 */
    private MeterRegistry registry;

    /** 被测对象。 */
    private SecurityMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new SecurityMetrics(registry);
    }

    @Test
    void cryptoDecryptLegacy_should_increment_with_tenant_tag() {
        // 同一租户连续两次解密旧密文 → tenant_id=1 的计数应为 2
        metrics.cryptoDecryptLegacy(1L);
        metrics.cryptoDecryptLegacy(1L);
        // 另一租户独立分桶 → tenant_id=2 的计数应为 1
        metrics.cryptoDecryptLegacy(2L);

        Counter tenant1 = registry.find("crypto_decrypt_legacy")
                .tag("tenant_id", "1")
                .counter();
        Counter tenant2 = registry.find("crypto_decrypt_legacy")
                .tag("tenant_id", "2")
                .counter();

        assertThat(tenant1).isNotNull();
        assertThat(tenant1.count()).isEqualTo(2.0);
        assertThat(tenant2).isNotNull();
        assertThat(tenant2.count()).isEqualTo(1.0);
    }

    @Test
    void cryptoDecryptFailed_should_increment_total_counter() {
        metrics.cryptoDecryptFailed();
        metrics.cryptoDecryptFailed();
        metrics.cryptoDecryptFailed();

        Counter counter = registry.find("crypto_decrypt_failed").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(3.0);
    }

    @Test
    void ssrfBlocked_should_increment_with_stage_tag() {
        metrics.ssrfBlocked("dns_resolve");
        metrics.ssrfBlocked("redirect");
        metrics.ssrfBlocked("dns_resolve");

        Counter dnsResolve = registry.find("ssrf_blocked")
                .tag("stage", "dns_resolve")
                .counter();
        Counter redirect = registry.find("ssrf_blocked")
                .tag("stage", "redirect")
                .counter();

        assertThat(dnsResolve).isNotNull();
        assertThat(dnsResolve.count()).isEqualTo(2.0);
        assertThat(redirect).isNotNull();
        assertThat(redirect.count()).isEqualTo(1.0);
    }

    @Test
    void mimeMismatch_should_increment_with_ext_and_detected_tags() {
        metrics.mimeMismatch("png", "application/x-php");
        metrics.mimeMismatch("png", "application/x-php");
        metrics.mimeMismatch("jpg", "application/x-msdownload");

        Counter phpAsPng = registry.find("mime_mismatch")
                .tag("ext", "png")
                .tag("detected", "application/x-php")
                .counter();
        Counter exeAsJpg = registry.find("mime_mismatch")
                .tag("ext", "jpg")
                .tag("detected", "application/x-msdownload")
                .counter();

        assertThat(phpAsPng).isNotNull();
        assertThat(phpAsPng.count()).isEqualTo(2.0);
        assertThat(exeAsJpg).isNotNull();
        assertThat(exeAsJpg.count()).isEqualTo(1.0);
    }

    @Test
    void loginLockout503_should_increment_total_counter() {
        metrics.loginLockout503();

        Counter counter = registry.find("login_lockout_503").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void wsLegacyTokenUsed_should_increment_total_counter() {
        metrics.wsLegacyTokenUsed();
        metrics.wsLegacyTokenUsed();

        Counter counter = registry.find("ws_legacy_token_used").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(2.0);
    }

    @Test
    void tenantViolation_should_increment_with_reason_tag() {
        metrics.tenantViolation("subdomain_session_mismatch");
        metrics.tenantViolation("missing_tenant_id");

        Counter mismatch = registry.find("tenant_violation")
                .tag("reason", "subdomain_session_mismatch")
                .counter();
        Counter missing = registry.find("tenant_violation")
                .tag("reason", "missing_tenant_id")
                .counter();

        assertThat(mismatch).isNotNull();
        assertThat(mismatch.count()).isEqualTo(1.0);
        assertThat(missing).isNotNull();
        assertThat(missing.count()).isEqualTo(1.0);
    }

    @Test
    void rateLimit429_should_increment_with_route_tag() {
        // 不同 path variable 必须使用相同的路由模板分桶
        metrics.rateLimit429("/api/v1/posts/{id}");
        metrics.rateLimit429("/api/v1/posts/{id}");
        metrics.rateLimit429("/api/v1/messages");

        Counter postsId = registry.find("rate_limit_429")
                .tag("route", "/api/v1/posts/{id}")
                .counter();
        Counter messages = registry.find("rate_limit_429")
                .tag("route", "/api/v1/messages")
                .counter();

        assertThat(postsId).isNotNull();
        assertThat(postsId.count()).isEqualTo(2.0);
        assertThat(messages).isNotNull();
        assertThat(messages.count()).isEqualTo(1.0);
    }

    @Test
    void sessionForcedLogout_should_increment_with_action_tag() {
        metrics.sessionForcedLogout("PASSWORD_CHANGE");
        metrics.sessionForcedLogout("PASSWORD_RESET");
        metrics.sessionForcedLogout("PASSWORD_CHANGE");

        Counter pwdChange = registry.find("session_forced_logout")
                .tag("action", "PASSWORD_CHANGE")
                .counter();
        Counter pwdReset = registry.find("session_forced_logout")
                .tag("action", "PASSWORD_RESET")
                .counter();

        assertThat(pwdChange).isNotNull();
        assertThat(pwdChange.count()).isEqualTo(2.0);
        assertThat(pwdReset).isNotNull();
        assertThat(pwdReset.count()).isEqualTo(1.0);
    }
}
