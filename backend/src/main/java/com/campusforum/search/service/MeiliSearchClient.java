package com.campusforum.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class MeiliSearchClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String host;
    private final String apiKey;
    private final boolean active;

    public MeiliSearchClient(@Value("${search.type:mysql}") String type,
                             @Value("${search.meilisearch.host:http://localhost:7700}") String host,
                             @Value("${search.meilisearch.api-key:}") String apiKey) {
        this.host = host;
        this.apiKey = apiKey;
        this.active = "meilisearch".equals(type);
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    public List<Map<String, Object>> search(String index, String query, int limit) {
        if (!active) return List.of();
        try {
            Map<String, Object> body = Map.of("q", query, "limit", limit);
            Map resp = post("/indexes/" + index + "/search", body);
            List<Map<String, Object>> hits = (List<Map<String, Object>>) resp.get("hits");
            return hits != null ? hits : List.of();
        } catch (Exception e) {
            log.debug("MeiliSearch search failed for index={}: {}", index, e.getMessage());
            return List.of();
        }
    }

    public void indexDocuments(String index, List<Map<String, Object>> documents) {
        if (!active || documents.isEmpty()) return;
        try {
            ensureIndex(index);
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

    private void ensureIndex(String index) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (apiKey != null && !apiKey.isBlank()) headers.set("Authorization", "Bearer " + apiKey);

            // Check if exists
            ResponseEntity<String> getResp = restTemplate.exchange(
                    host + "/indexes/" + index, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            if (getResp.getStatusCode().is2xxSuccessful()) return;
        } catch (Exception ignored) {
        }
        // Create index
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (apiKey != null && !apiKey.isBlank()) headers.set("Authorization", "Bearer " + apiKey);
            Map<String, Object> body = Map.of("uid", index, "primaryKey", "id");
            restTemplate.exchange(host + "/indexes", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
            log.info("Created MeiliSearch index: {}", index);
        } catch (Exception e) {
            log.debug("Failed to create MeiliSearch index {}: {}", index, e.getMessage());
        }
    }

    private Map post(String path, Object body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (apiKey != null && !apiKey.isBlank()) headers.set("Authorization", "Bearer " + apiKey);
            ResponseEntity<Map> resp = restTemplate.exchange(host + path, HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);
            return resp.getBody();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
