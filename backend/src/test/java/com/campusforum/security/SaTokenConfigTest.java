package com.campusforum.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SaTokenConfig 单元测试：验证排除路径配置正确。
 *
 * <p>确保 forgot-password 和 reset-password 在未登录情况下可访问，
 * 而 /me 仍需登录。</p>
 */
class SaTokenConfigTest {

    @Test
    @DisplayName("email-code、forgot-password 和 reset-password 应在排除路径中")
    void shouldExcludeForgotAndResetPasswordPaths() throws Exception {
        SaTokenConfig config = new SaTokenConfig();
        InterceptorRegistry registry = new InterceptorRegistry();
        config.addInterceptors(registry);

        // 通过反射获取注册的拦截器配置
        List<String> excludePatterns = getExcludePatterns(registry);

        assertThat(excludePatterns)
                .contains("/api/v1/auth/email-code")
                .contains("/api/v1/auth/forgot-password")
                .contains("/api/v1/auth/reset-password");
    }

    @Test
    @DisplayName("/me 不应在排除路径中（需要登录）")
    void shouldNotExcludeMePath() throws Exception {
        SaTokenConfig config = new SaTokenConfig();
        InterceptorRegistry registry = new InterceptorRegistry();
        config.addInterceptors(registry);

        List<String> excludePatterns = getExcludePatterns(registry);

        assertThat(excludePatterns)
                .doesNotContain("/api/v1/auth/me");
    }

    @Test
    @DisplayName("login 和 register 仍在排除路径中")
    void shouldExcludeLoginAndRegisterPaths() throws Exception {
        SaTokenConfig config = new SaTokenConfig();
        InterceptorRegistry registry = new InterceptorRegistry();
        config.addInterceptors(registry);

        List<String> excludePatterns = getExcludePatterns(registry);

        assertThat(excludePatterns)
                .contains("/api/v1/auth/login")
                .contains("/api/v1/auth/register");
    }

    /**
     * 通过反射从 InterceptorRegistry 中提取排除路径列表。
     */
    @SuppressWarnings("unchecked")
    private List<String> getExcludePatterns(InterceptorRegistry registry) throws Exception {
        // InterceptorRegistry 内部维护 registrations 列表
        Field registrationsField = InterceptorRegistry.class.getDeclaredField("registrations");
        registrationsField.setAccessible(true);
        List<?> registrations = (List<?>) registrationsField.get(registry);

        assertThat(registrations).hasSize(1);
        InterceptorRegistration registration = (InterceptorRegistration) registrations.get(0);

        // InterceptorRegistration 内部维护 excludePatterns
        Field excludeField = InterceptorRegistration.class.getDeclaredField("excludePatterns");
        excludeField.setAccessible(true);
        return (List<String>) excludeField.get(registration);
    }
}
