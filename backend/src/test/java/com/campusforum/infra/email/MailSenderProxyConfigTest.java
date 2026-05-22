package com.campusforum.infra.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mock.env.MockEnvironment;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class MailSenderProxyConfigTest {

    @Test
    @DisplayName("配置 HTTP 代理时应写入 JavaMail SMTP 代理属性")
    void shouldApplyHttpProxyProperties() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("SMTP_PROXY_HOST", "192.168.150.1")
                .withProperty("SMTP_PROXY_PORT", "7897")
                .withProperty("SMTP_SSL_PROTOCOLS", "TLSv1.2");
        BeanPostProcessor processor = MailSenderProxyConfig.mailSenderProxyPostProcessor(environment);
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

        processor.postProcessAfterInitialization(mailSender, "mailSender");

        Properties properties = mailSender.getJavaMailProperties();
        assertThat(properties.getProperty("mail.smtp.proxy.host")).isEqualTo("192.168.150.1");
        assertThat(properties.getProperty("mail.smtp.proxy.port")).isEqualTo("7897");
        assertThat(properties.getProperty("mail.smtp.ssl.protocols")).isEqualTo("TLSv1.2");
    }

    @Test
    @DisplayName("未配置代理时不应写入空代理属性")
    void shouldNotWriteBlankProxyProperties() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("SMTP_PROXY_HOST", "")
                .withProperty("SMTP_PROXY_PORT", "");
        BeanPostProcessor processor = MailSenderProxyConfig.mailSenderProxyPostProcessor(environment);
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

        processor.postProcessAfterInitialization(mailSender, "mailSender");

        Properties properties = mailSender.getJavaMailProperties();
        assertThat(properties).doesNotContainKey("mail.smtp.proxy.host");
        assertThat(properties).doesNotContainKey("mail.smtp.proxy.port");
    }

    @Test
    @DisplayName("未配置 HTTP 代理时应支持 SOCKS 代理")
    void shouldApplySocksProxyWhenHttpProxyIsAbsent() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("SMTP_SOCKS_HOST", "192.168.150.1")
                .withProperty("SMTP_SOCKS_PORT", "7897");
        BeanPostProcessor processor = MailSenderProxyConfig.mailSenderProxyPostProcessor(environment);
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

        processor.postProcessAfterInitialization(mailSender, "mailSender");

        Properties properties = mailSender.getJavaMailProperties();
        assertThat(properties.getProperty("mail.smtp.socks.host")).isEqualTo("192.168.150.1");
        assertThat(properties.getProperty("mail.smtp.socks.port")).isEqualTo("7897");
    }
}
