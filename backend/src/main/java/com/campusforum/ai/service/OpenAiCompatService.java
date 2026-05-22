package com.campusforum.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "openai")
public class OpenAiCompatService implements AiService {

    private static final int CONNECT_TIMEOUT_MS = 8000;
    private static final int READ_TIMEOUT_MS = 30000;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public OpenAiCompatService(@Value("${ai.base-url}") String baseUrl,
                               @Value("${ai.api-key}") String apiKey,
                               @Value("${ai.model:deepseek-chat}") String model) {
        this.restTemplate = createRestTemplate();
        this.objectMapper = new ObjectMapper();
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey;
        this.model = model == null || model.isBlank() ? "deepseek-chat" : model;
    }

    @Override
    public String summarize(String content) {
        if (content == null || content.isBlank()) return "";
        String prompt = "请用简洁的语言总结以下内容，不超过150字：\n\n" + content;
        return chatOne(prompt);
    }

    @Override
    public RiskResult moderate(String content) {
        if (content == null || content.isBlank()) return new RiskResult(0, "");
        String prompt = "请审核以下内容，判断是否包含违规信息（如暴力、色情、诈骗、人身攻击等）。请回复JSON格式：{\"level\": 0-2, \"reason\": \"原因\"}，其中0=安全，1=疑似，2=违规。\n\n内容：" + content;
        String resp = chatOne(prompt);
        try {
            Map<String, Object> map = objectMapper.readValue(resp.replaceAll("```json|```", "").trim(), Map.class);
            int level = map.get("level") instanceof Integer ? (int) map.get("level") : 1;
            String reason = (String) map.getOrDefault("reason", "AI审核");
            return new RiskResult(Math.max(0, Math.min(2, level)), reason);
        } catch (Exception e) {
            return new RiskResult(0, "");
        }
    }

    @Override
    public List<String> recommendTags(String title, String content) {
        String text = (title != null ? title + " " : "") + (content != null ? content : "");
        if (text.isBlank()) return List.of();
        String prompt = "请为以下内容推荐3-5个简短的中文标签（逗号分隔，每个标签2-4字）：\n\n" + text;
        String resp = chatOne(prompt);
        if (resp.isBlank()) return List.of();
        List<String> tags = new ArrayList<>();
        for (String tag : resp.split("[,，]")) {
            String t = tag.trim().replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9]", "");
            if (!t.isBlank() && t.length() <= 6) tags.add(t);
        }
        return tags.size() <= 5 ? tags : tags.subList(0, 5);
    }

    @Override
    public String chat(List<ChatMessage> messages, String context) {
        List<Map<String, String>> chatMessages = new ArrayList<>();
        if (context != null && !context.isBlank()) {
            chatMessages.add(Map.of("role", "system", "content", context));
        }
        chatMessages.add(Map.of("role", "system", "content", "你是 CampusForum 高校轻量化学习社群平台的AI助手，请友好、简洁地回答用户问题。"));
        if (messages != null) {
            for (ChatMessage msg : messages) {
                chatMessages.add(Map.of("role", msg.role(), "content", msg.content()));
            }
        }
        return chatCompletion(chatMessages);
    }

    private String chatOne(String prompt) {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", prompt)
        );
        return chatCompletion(messages);
    }

    private String chatCompletion(List<Map<String, String>> messages) {
        if (apiKey == null || apiKey.isBlank()) {
            return "AI 服务未配置 API Key";
        }
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", messages,
                    "temperature", 0.3,
                    "max_tokens", 500
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            ResponseEntity<Map> resp = restTemplate.exchange(
                    baseUrl + "/chat/completions",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    Map.class);

            if (resp.getBody() != null) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.getBody().get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    if (message != null) {
                        return (String) message.getOrDefault("content", "");
                    }
                }
            }
        } catch (RestClientResponseException e) {
            log.warn("OpenAI API call rejected: status={}, body={}", e.getStatusCode().value(), safeErrorBody(e.getResponseBodyAsString()));
            return "AI 服务调用失败：模型服务返回 " + e.getStatusCode().value() + "，请检查 API Key、模型名称或服务额度。";
        } catch (ResourceAccessException e) {
            log.warn("OpenAI API connection failed: {}", e.getMessage());
            return "AI 服务调用失败：无法连接到模型服务，请检查服务器网络或 API Base URL。";
        } catch (Exception e) {
            log.warn("OpenAI API call failed: {}", e.getMessage());
            return "AI 服务调用失败：后端解析模型响应异常，请稍后重试。";
        }
        return "AI 服务调用失败：模型服务没有返回有效内容。";
    }

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // 外部大模型接口不可达时不应让用户一直等待；失败信息会返回给前端用于明确提示。
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return new RestTemplate(factory);
    }

    private static String safeErrorBody(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.length() <= 500 ? value : value.substring(0, 500) + "...";
    }

    private static String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            return "https://api.deepseek.com/v1";
        }
        String trimmed = value.strip();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.endsWith("/v1") ? trimmed : trimmed + "/v1";
    }
}
