package com.campusforum.search.service;

import com.campusforum.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MeiliSearch HTTP 客户端。
 *
 * <p>安全加固：写入文档时自动注入 {@code tenantId} 字段；搜索时自动追加
 * {@code filter: "tenantId = X"}，避免 multi 模式下跨租户数据泄漏。
 * 索引创建时自动设置 {@code filterableAttributes: ["tenantId"]}。</p>
 */
@Slf4j
@Component
public class MeiliSearchClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String host;
    private final String apiKey;
    private final boolean active;

    /** 已配置过 filterableAttributes 的索引缓存，避免每次写入都打配置接口。 */
    private final Map<String, Boolean> filterableConfigured = new ConcurrentHashMap<>();

    public MeiliSearchClient(@Value("${search.type:mysql}") String type,
                             @Value("${search.meilisearch.host:http://localhost:7700}") String host,
                             @Value("${search.meilisearch.api-key:}") String apiKey) {
        this.host = host;
        this.apiKey = apiKey;
        this.active = "meilisearch".equals(type);
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 在指定索引中搜索。会自动追加 tenantId filter 防止跨租户数据泄漏。
     * 调用方传入的 tenantId 应来自服务端权威来源（TenantContext 或 Sa-Token Session），
     * 而非客户端请求体。
     */
    public List<Map<String, Object>> search(String index, String query, int limit, Long tenantId) {
        if (!active) return List.of();
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("q", query);
            body.put("limit", limit);
            if (tenantId != null) {
                body.put("filter", "tenantId = " + tenantId);
            }
            Map resp = post("/indexes/" + index + "/search", body);
            List<Map<String, Object>> hits = (List<Map<String, Object>>) resp.get("hits");
            return hits != null ? hits : List.of();
        } catch (Exception e) {
            log.debug("MeiliSearch search failed for index={}: {}", index, e.getMessage());
            return List.of();
        }
    }

    /**
     * 兼容旧调用入口：从 TenantContext 自动取 tenantId。
     */
    public List<Map<String, Object>> search(String index, String query, int limit) {
        return search(index, query, limit, TenantContext.getTenantId());
    }

    public void indexDocuments(String index, List<Map<String, Object>> documents) {
        if (!active || documents.isEmpty()) return;
        try {
            ensureIndex(index);
            // 写入前为每条文档注入 tenantId（若调用方未显式提供）
            Long ctxTenant = TenantContext.getTenantId();
            for (Map<String, Object> doc : documents) {
                if (!doc.containsKey("tenantId") && ctxTenant != null) {
                    doc.put("tenantId", ctxTenant);
                }
            }
            post("/indexes/" + index + "/documents", documents);
            log.debug("Indexed {} documents to {}", documents.size(), index);
        } catch (Exception e) {
            log.debug("MeiliSearch index failed for index={}: {}", index, e.getMessage());
        }
    }

    public void indexDocument(String index, Map<String, Object> document) {
        indexDocuments(index, List.of(document));
    }

    public void deleteDocument(String index, Long id) {
        if (!active) return;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (apiKey != null && !apiKey.isBlank()) headers.set("Authorization", "Bearer " + apiKey);
            restTemplate.exchange(host + "/indexes/" + index + "/documents/" + id,
                    HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
        } catch (Exception e) {
            log.debug("MeiliSearch delete failed for index={}, id={}: {}", index, id, e.getMessage());
        }
    }

    /**
     * 确保索引存在，并已配置 tenantId 为可过滤属性。
     */
    private void ensureIndex(String index) {
        boolean indexExisted = false;
        try {
            HttpHeaders headers = jsonHeaders();
            ResponseEntity<String> getResp = restTemplate.exchange(
                    host + "/indexes/" + index, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            indexExisted = getResp.getStatusCode().is2xxSuccessful();
        } catch (Exception ignored) {
        }
        if (!indexExisted) {
            try {
                Map<String, Object> body = Map.of("uid", index, "primaryKey", "id");
                restTemplate.exchange(host + "/indexes", HttpMethod.POST,
                        new HttpEntity<>(body, jsonHeaders()), Map.class);
                log.info("Created MeiliSearch index: {}", index);
            } catch (Exception e) {
                log.debug("Failed to create MeiliSearch index {}: {}", index, e.getMessage());
            }
        }
        configureFilterableAttributes(index);
    }

    /**
     * 把 tenantId 注册为可过滤属性。索引启动期一次性下发，缓存避免重复请求。
     */
    private void configureFilterableAttributes(String index) {
        if (Boolean.TRUE.equals(filterableConfigured.get(index))) return;
        try {
            HttpEntity<List<String>> entity = new HttpEntity<>(List.of("tenantId"), jsonHeaders());
            restTemplate.exchange(
                    host + "/indexes/" + index + "/settings/filterable-attributes",
                    HttpMethod.PUT, entity, String.class);
            filterableConfigured.put(index, Boolean.TRUE);
            log.info("MeiliSearch filterable-attributes configured for index={}", index);
        } catch (Exception e) {
            log.debug("Failed to configure filterable-attributes for {}: {}", index, e.getMessage());
        }
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) headers.set("Authorization", "Bearer " + apiKey);
        return headers;
    }

    private Map post(String path, Object body) {
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(host + path, HttpMethod.POST,
                    new HttpEntity<>(body, jsonHeaders()), Map.class);
            return resp.getBody();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
