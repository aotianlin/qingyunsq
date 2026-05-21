package com.campusforum.infra.ratelimit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    private LimitConfig authenticated = new LimitConfig(200, 60);
    private LimitConfig anonymous = new LimitConfig(100, 60);

    /** 按端点覆盖限流配置，key 为 "METHOD path" 格式 */
    private Map<String, LimitConfig> overrides = new HashMap<>();

    /** 排除的路径模式 */
    private List<String> excludePatterns = List.of("/actuator/**", "/api/v1/auth/login");

    @Data
    public static class LimitConfig {
        private int maxRequests;
        private int windowSeconds;

        public LimitConfig() {
            this.maxRequests = 200;
            this.windowSeconds = 60;
        }

        public LimitConfig(int maxRequests, int windowSeconds) {
            this.maxRequests = maxRequests;
            this.windowSeconds = windowSeconds;
        }
    }
}
