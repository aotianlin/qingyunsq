package com.campusforum.ai.service;

import com.campusforum.tenant.TenantContext;
import com.campusforum.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Primary
@Service
@RequiredArgsConstructor
public class TenantAwareAiService implements AiService {

    private final TenantService tenantService;
    private final MockAiService mockAiService;

    @Override
    public String summarize(String content) {
        return delegate().summarize(content);
    }

    @Override
    public RiskResult moderate(String content) {
        return delegate().moderate(content);
    }

    @Override
    public List<String> recommendTags(String title, String content) {
        return delegate().recommendTags(title, content);
    }

    @Override
    public String chat(List<ChatMessage> messages, String context) {
        return delegate().chat(messages, context);
    }

    @Override
    public boolean checkRelevance(String theme, String content) {
        return delegate().checkRelevance(theme, content);
    }

    private AiService delegate() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            return mockAiService;
        }

        try {
            Map<String, String> config = tenantService.resolveAiCredentials(tenantId);
            String provider = config.get("provider");
            String apiKey = config.get("apiKey");
            String baseUrl = config.get("baseUrl");
            String model = config.get("model");

            // 管理端的 AI 配置按租户保存；运行时每次读取当前租户配置，避免重启后端才生效。
            if ("openai".equalsIgnoreCase(provider) && apiKey != null && !apiKey.isBlank()) {
                // 调用前再次校验 baseUrl 不指向私网，避免历史脏数据导致 SSRF
                try {
                    com.campusforum.infra.security.PrivateNetworkValidator.requirePublic(baseUrl, true);
                } catch (IllegalArgumentException ex) {
                    log.warn("Tenant AI baseUrl rejected (SSRF guard): tenantId={}, error={}",
                            tenantId, ex.getMessage());
                    return mockAiService;
                }
                return new OpenAiCompatService(baseUrl, apiKey, model);
            }
        } catch (Exception e) {
            log.warn("Tenant AI config unavailable, fallback to mock: tenantId={}, error={}", tenantId, e.getMessage());
        }

        return mockAiService;
    }

    private String stringValue(Object value) {
        return value instanceof String text ? text : null;
    }
}
