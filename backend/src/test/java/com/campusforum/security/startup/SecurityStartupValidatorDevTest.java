package com.campusforum.security.startup;

import com.campusforum.infra.ratelimit.RateLimitProperties;
import com.campusforum.infra.security.SecurityProperties;
import com.campusforum.infra.security.SecurityStartupValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.mock.env.MockEnvironment;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SecurityStartupValidator dev profile 零摩擦分支单元测试（T1.3）。
 *
 * <p>验证"dev profile 上对弱默认值仅 WARN 不阻断启动，但全 profile 强制约束的校验
 * （如 rate-limit.exclude-patterns）仍生效"的双重策略。</p>
 */
class SecurityStartupValidatorDevTest {

    private MockEnvironment env;
    private SecurityProperties props;
    private RateLimitProperties rateLimitProps;
    private final ApplicationArguments args = new ApplicationArguments() {
        @Override public String[] getSourceArgs() { return new String[0]; }
        @Override public java.util.Set<String> getOptionNames() { return java.util.Set.of(); }
        @Override public boolean containsOption(String name) { return false; }
        @Override public List<String> getOptionValues(String name) { return List.of(); }
        @Override public List<String> getNonOptionArgs() { return List.of(); }
    };

    /**
     * dev profile + application-dev.yml 中描述的弱默认值（含 dev-only-change-me 前缀，
     * 长度 ≥ 32），模拟开发者本地启动场景。
     */
    @BeforeEach
    void setUp() {
        env = new MockEnvironment();
        env.addActiveProfile("dev");
        // dev 默认弱密码（与 my-redis 历史容器一致）
        env.setProperty("spring.data.redis.password", "123456");

        props = new SecurityProperties();
        // 含 dev-only-change-me token 但 ≥ 32 字节，与 application-dev.yml 默认值一致
        props.getCrypto().setMasterKey("dev-only-change-me-master-key-32bytes-padding!!");
        props.getCrypto().setLegacyMode(false);
        props.setSignedUrlSecret("dev-only-change-me-signed-url-secret-32bytes-padding!!");
        // 模拟 application.yml 配置的兼容期 cutover
        props.getCrypto().setLegacyCutoverDate(LocalDate.now().minusDays(1));
        props.getWsTicket().setEnforced(false);
        props.getWsTicket().setEnforcedCutoverDate(LocalDate.now().minusDays(1));

        rateLimitProps = new RateLimitProperties();
        rateLimitProps.setExcludePatterns(new ArrayList<>(List.of("/actuator/**")));
    }

    private SecurityStartupValidator newValidator() {
        return new SecurityStartupValidator(props, env, rateLimitProps);
    }

    @Test
    @DisplayName("dev_weakDefaults_doNotThrow：dev profile + 全部弱默认值 → 不抛（仅 WARN）")
    void dev_weakDefaults_doNotThrow() {
        // 哪怕 cutover 已过、ws-ticket 未 enforced、master/secret 命中弱 token，dev 都不抛
        assertThatCode(() -> newValidator().run(args)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("dev_redisPassword_short_doesNotThrow：dev profile + 短 Redis 密码 → 不抛")
    void dev_redisPassword_short_doesNotThrow() {
        env.setProperty("spring.data.redis.password", "1");

        assertThatCode(() -> newValidator().run(args)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("dev_signedUrlSecret_short_doesNotThrow：dev profile + 短 signed-url-secret → 不抛")
    void dev_signedUrlSecret_short_doesNotThrow() {
        // 长度 < 32 在 prod 抛错，dev 仅 WARN
        props.setSignedUrlSecret("short-15bytes!!");

        assertThatCode(() -> newValidator().run(args)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("dev_masterKey_blank_throws：master-key 缺失即使在 dev 也应抛")
    void dev_masterKey_blank_throws() {
        // 缺失 master-key 是配置错误，所有 profile 都应抛错
        props.getCrypto().setMasterKey("");

        assertThatThrownBy(() -> newValidator().run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("master-key 未配置");
    }

    @Test
    @DisplayName("dev_masterKey_tooShort_throws：长度 < 32 即使在 dev 也应抛")
    void dev_masterKey_tooShort_throws() {
        // master-key 长度不足是基础约束，dev 也不能放过
        props.getCrypto().setMasterKey("dev-only-too-short");

        assertThatThrownBy(() -> newValidator().run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    @DisplayName("dev_exclude_login_stillThrows：rate-limit 排除敏感路径在 dev 也生效（避免配错被发到 prod）")
    void dev_exclude_login_stillThrows() {
        rateLimitProps.setExcludePatterns(List.of("/api/v1/auth/login"));

        assertThatThrownBy(() -> newValidator().run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("敏感路径不可被加入 rate-limit.exclude-patterns");
    }
}
