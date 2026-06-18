package com.campusforum.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "ai.providers")
public class AiProviderProperties {

    private Provider deepseek = new Provider("https://api.deepseek.com", "");
    private Provider mimo = new Provider("https://api.xiaomimimo.com/v1", "");

    public Provider resolveByModel(String model) {
        if (model == null || model.isBlank()) {
            return deepseek.withModel("deepseek-v4-flash");
        }
        if (model.startsWith("deepseek-")) {
            return deepseek.withModel(model);
        }
        if (model.startsWith("mimo-")) {
            return mimo.withModel(model);
        }
        return null;
    }

    public Map<String, String> supportedModels() {
        return Map.of(
                "deepseek-v4-flash", "DeepSeek V4 Flash",
                "deepseek-v4-pro", "DeepSeek V4 Pro",
                "mimo-v2.5", "MiMo 2.5",
                "mimo-v2.5-pro", "MiMo 2.5 Pro"
        );
    }

    @Data
    public static class Provider {
        private String baseUrl;
        private String apiKey;
        private String model;

        public Provider() {
        }

        public Provider(String baseUrl, String apiKey) {
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
        }

        public Provider withModel(String model) {
            Provider copy = new Provider();
            copy.baseUrl = this.baseUrl;
            copy.apiKey = this.apiKey;
            copy.model = model;
            return copy;
        }
    }
}
