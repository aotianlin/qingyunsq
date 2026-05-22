package com.campusforum.infra.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

import java.util.Properties;

/**
 * SMTP 发送器的代理配置。
 *
 * <p>这里不改系统代理，只在邮件发送器上单独注入代理参数，避免影响
 * 其他 HTTP 客户端和当前虚拟机里的通用网络访问。</p>
 */
@Slf4j
@Configuration
public class MailSenderProxyConfig {

    @Bean
    public static BeanPostProcessor mailSenderProxyPostProcessor(Environment environment) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof JavaMailSenderImpl mailSender) {
                    applyOptionalProxySettings(mailSender, environment);
                }
                return bean;
            }
        };
    }

    /**
     * 只在显式配置时写入代理参数，避免空值污染 JavaMail 属性。
     */
    private static void applyOptionalProxySettings(JavaMailSenderImpl mailSender, Environment environment) {
        Properties mailProperties = mailSender.getJavaMailProperties();

        String proxyHost = trimToNull(environment.getProperty("SMTP_PROXY_HOST"));
        Integer proxyPort = readInteger(environment, "SMTP_PROXY_PORT");
        String proxyUser = trimToNull(environment.getProperty("SMTP_PROXY_USERNAME"));
        String proxyPassword = trimToNull(environment.getProperty("SMTP_PROXY_PASSWORD"));

        String socksHost = trimToNull(environment.getProperty("SMTP_SOCKS_HOST"));
        Integer socksPort = readInteger(environment, "SMTP_SOCKS_PORT");

        String sslProtocols = trimToNull(environment.getProperty("SMTP_SSL_PROTOCOLS"));
        String sslTrust = trimToNull(environment.getProperty("SMTP_SSL_TRUST"));
        String sslCheck = trimToNull(environment.getProperty("SMTP_SSL_CHECK_SERVER_IDENTITY"));

        if (StringUtils.hasText(proxyHost)) {
            // HTTP CONNECT 代理：优先用于宿主机 7897 这种 mixed/http 代理入口。
            mailProperties.put("mail.smtp.proxy.host", proxyHost);
            if (proxyPort != null && proxyPort > 0) {
                mailProperties.put("mail.smtp.proxy.port", String.valueOf(proxyPort));
            }
            if (StringUtils.hasText(proxyUser)) {
                mailProperties.put("mail.smtp.proxy.user", proxyUser);
            }
            if (StringUtils.hasText(proxyPassword)) {
                mailProperties.put("mail.smtp.proxy.password", proxyPassword);
            }
            log.info("SMTP web proxy enabled: {}:{}", proxyHost, proxyPort != null ? proxyPort : "default");
        } else if (StringUtils.hasText(socksHost)) {
            // SOCKS 代理：作为备用方案，方便后续切换到 socks5 入口。
            mailProperties.put("mail.smtp.socks.host", socksHost);
            if (socksPort != null && socksPort > 0) {
                mailProperties.put("mail.smtp.socks.port", String.valueOf(socksPort));
            }
            log.info("SMTP SOCKS proxy enabled: {}:{}", socksHost, socksPort != null ? socksPort : "default");
        }

        if (StringUtils.hasText(sslProtocols)) {
            mailProperties.put("mail.smtp.ssl.protocols", sslProtocols);
        }
        if (StringUtils.hasText(sslTrust)) {
            mailProperties.put("mail.smtp.ssl.trust", sslTrust);
        }
        if (StringUtils.hasText(sslCheck)) {
            mailProperties.put("mail.smtp.ssl.checkserveridentity", sslCheck);
        }
    }

    /**
     * 读取整数配置，非法值直接忽略，避免启动失败。
     */
    private static Integer readInteger(Environment environment, String key) {
        String value = trimToNull(environment.getProperty(key));
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
