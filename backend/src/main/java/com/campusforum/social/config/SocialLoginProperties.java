package com.campusforum.social.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "social")
public class SocialLoginProperties {

    private final Qq qq = new Qq();
    private final Github github = new Github();
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 8000;

    @Data
    public static class Qq {
        private String appId;
        private String appKey;
    }

    @Data
    public static class Github {
        private String clientId;
        private String clientSecret;
    }
}
