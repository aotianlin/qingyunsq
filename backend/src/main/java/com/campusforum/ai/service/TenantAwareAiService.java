package com.campusforum.ai.service;

import com.campusforum.ai.config.AiHttpProperties;
import com.campusforum.tenant.TenantContext;
import com.campusforum.tenant.service.TenantService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Primary
@Service
@RequiredArgsConstructor
public class TenantAwareAiService implements AiService {

    private final TenantService tenantService;
    private final MockAiService mockAiService;
    private final AiHttpProperties httpProperties;

    /**
     * 按 (tenantId, baseUrl, apiKey, model) 缓存 OpenAiCompatService 实例，避免每次请求重建 RestTemplate。
     *
     * 热生效：管理台改完配置 → DB 变更 → 下一次请求 key 变 → cache miss → new 实例 → 旧实例由 expireAfterWrite 自动回收。
     * 不需要主动 invalidate。
     *
     * timeout 配置（ai.http.*）不进 key — 变更需要重启生效。
     */
    private final Cache<AiClientKey, OpenAiCompatService> clientCache = Caffeine.newBuilder()
            .maximumSize(64)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();

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

    @Override
    public PostCardResult generatePostCard(String title,
                                           String content,
                                           String postType,
                                           String tags,
                                           List<String> recentComments) {
        return delegate().generatePostCard(title, content, postType, tags, recentComments);
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
            // baseUrl/apiKey 缺一不可，否则 fallback mock 而不是让 OpenAiCompatService 拼出坏 URL。
            if ("openai".equalsIgnoreCase(provider)
                    && apiKey != null && !apiKey.isBlank()
                    && baseUrl != null && !baseUrl.isBlank()) {
                // 调用前再次校验 baseUrl 不指向私网，避免历史脏数据导致 SSRF
                try {
                    com.campusforum.infra.security.PrivateNetworkValidator.requirePublic(baseUrl, true);
                } catch (IllegalArgumentException ex) {
                    log.warn("Tenant AI baseUrl rejected (SSRF guard): tenantId={}, error={}",
                            tenantId, ex.getMessage());
                    return mockAiService;
                }
                AiClientKey key = new AiClientKey(tenantId, baseUrl, apiKey, model);
                return clientCache.get(key, k -> new OpenAiCompatService(
                        k.baseUrl(), k.apiKey(), k.model(),
                        httpProperties.getConnectTimeout(), httpProperties.getReadTimeout()));
            }
            if ("openai".equalsIgnoreCase(provider)) {
                log.warn("Tenant {} configured openai but baseUrl/apiKey is blank, fallback to mock", tenantId);
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
