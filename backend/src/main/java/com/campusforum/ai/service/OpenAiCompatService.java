package com.campusforum.ai.service;

import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.infra.security.SafeHttpClient;
import com.campusforum.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "openai")
public class OpenAiCompatService implements AiService {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(30);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    // 老构造函数（保留用于 @Value 注入，当 ai.provider=openai 时被 Spring 装配）
    public OpenAiCompatService(@Value("${ai.base-url}") String baseUrl,
                               @Value("${ai.api-key}") String apiKey,
                               @Value("${ai.model:deepseek-v4-flash}") String model) {
        this(baseUrl, apiKey, model, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT);
    }

    // 主构造函数：超时可配置。TenantAwareAiService 通过此方法注入 AiHttpProperties 值。
    public OpenAiCompatService(String baseUrl, String apiKey, String model,
                               Duration connectTimeout, Duration readTimeout) {
        this.restTemplate = createRestTemplate(connectTimeout, readTimeout);
        this.objectMapper = new ObjectMapper();
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey;
        this.model = model == null || model.isBlank() ? "deepseek-v4-flash" : model;
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
        String resp;
        try {
            resp = chatOne(prompt);
        } catch (BusinessException e) {
            // 上游 AI 服务调用失败 → fail-closed 走人工复核而不是抛 5xx，避免审核流程被卡死
            log.warn("AI moderation upstream call failed: {}", e.getMessage());
            return new RiskResult(1, "AI 审核服务不可用，请人工复核");
        }
        try {
            Map<String, Object> map = objectMapper.readValue(resp.replaceAll("```json|```", "").trim(), Map.class);
            int level = map.get("level") instanceof Integer ? (int) map.get("level") : 1;
            String reason = (String) map.getOrDefault("reason", "AI审核");
            return new RiskResult(Math.max(0, Math.min(2, level)), reason);
        } catch (Exception e) {
            // fail-closed：模型回包格式异常时不静默放行，落到"疑似"让上层做人工复核。
            log.warn("AI moderation parse failed, fallback to fail-closed: response={}, error={}",
                    safeErrorBody(resp), e.getMessage());
            return new RiskResult(1, "AI 审核结果解析失败，请人工复核");
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

    @Override
    public PostCardResult generatePostCard(String title,
                                           String content,
                                           String postType,
                                           String tags,
                                           List<String> recentComments) {
        if ((title == null || title.isBlank()) && (content == null || content.isBlank())) {
            return null;
        }
        // 截断输入，避免超长帖子打爆 token
        String safeTitle = title == null ? "" : title.strip();
        String safeContent = content == null ? "" : content.strip();
        if (safeContent.length() > 4000) safeContent = safeContent.substring(0, 4000) + "...(已截断)";

        StringBuilder commentsBlock = new StringBuilder();
        if (recentComments != null && !recentComments.isEmpty()) {
            int limit = Math.min(recentComments.size(), 20);
            for (int i = 0; i < limit; i++) {
                String c = recentComments.get(i);
                if (c == null || c.isBlank()) continue;
                String clipped = c.length() > 300 ? c.substring(0, 300) + "..." : c;
                commentsBlock.append("[").append(i + 1).append("] ").append(clipped).append('\n');
            }
        }

        String prompt = """
                你是校园论坛的内容理解助手。请基于下面这篇帖子和评论，生成结构化摘要卡片。

                帖子分类（用户自报，可能不准）：%s
                帖子标签：%s
                帖子标题：%s
                帖子正文：
                %s

                评论（按热度排序，前 %d 条）：
                %s

                要求：
                1. 只返回严格的 JSON，不要 markdown 代码块、不要任何额外解释。
                2. 字段说明：
                   - "tldr": 一句话核心结论，30-60 字。不要用"本文讨论了""作者认为"这类废话开头，直接给结论。
                   - "audience": 适合谁读，10-30 字，越具体越好（如"备考软工的同学"而不是"对编程感兴趣的人"）。
                   - "value_type": 在 ["提问","经验","资源","吐槽","招募","讨论"] 中选最贴切的一个。
                   - "read_minutes": 估计阅读时长（分钟，整数）。
                   - "comment_consensus": 评论区高赞共识答案；若评论少或无明显共识返回 null。
                   - "comment_disputes": 评论区主要争议点（最多 100 字）；若无明显争议返回 null。
                   - "highlights": 2-3 个核心关键词字符串数组，每个 2-6 字，用于色块标签呈现（如 ["六级备考","词汇技巧","真题资源"]）。要具体，不要"学习""分享"这种空泛词。
                3. 不确定的字段返回 null，不要编造。
                """.formatted(
                postType == null ? "未指定" : postType,
                tags == null || tags.isBlank() ? "无" : tags,
                safeTitle,
                safeContent.isBlank() ? "（仅标题）" : safeContent,
                commentsBlock.length() == 0 ? 0 : Math.min(recentComments == null ? 0 : recentComments.size(), 20),
                commentsBlock.length() == 0 ? "（暂无评论）" : commentsBlock
        );

        try {
            String resp = chatOne(prompt);
            if (resp == null || resp.isBlank() || resp.equals(AI_UPSTREAM_ERROR_MESSAGE)) {
                return null;
            }
            return parsePostCardJson(resp);
        } catch (Exception e) {
            log.warn("generatePostCard failed: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private PostCardResult parsePostCardJson(String resp) {
        // 模型可能返回 ```json ... ``` 包裹，先剥离
        String trimmed = resp.strip();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) trimmed = trimmed.substring(firstNewline + 1);
            int fenceEnd = trimmed.lastIndexOf("```");
            if (fenceEnd > 0) trimmed = trimmed.substring(0, fenceEnd);
            trimmed = trimmed.strip();
        }
        try {
            Map<String, Object> map = objectMapper.readValue(trimmed, Map.class);
            return new PostCardResult(
                    asString(map.get("tldr")),
                    asString(map.get("audience")),
                    asString(map.get("value_type")),
                    asInt(map.get("read_minutes")),
                    asString(map.get("comment_consensus")),
                    asString(map.get("comment_disputes")),
                    asStringList(map.get("highlights"))
            );
        } catch (Exception e) {
            log.warn("parsePostCardJson failed: payload={}", trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed);
            return null;
        }
    }

    private static String asString(Object v) {
        if (v == null) return null;
        String s = v.toString().strip();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }

    private static Integer asInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s.strip()); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object v) {
        if (!(v instanceof List<?> list)) return null;
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            String s = asString(item);
            if (s != null) result.add(s);
        }
        return result.isEmpty() ? null : result;
    }

    private String chatOne(String prompt) {
        List<Map<String, String>> messages = List.of(
                Map.of("role", "user", "content", prompt)
        );
        return chatCompletion(messages);
    }

    /**
     * 向上游发起 chat completion 请求。
     *
     * <p>失败时抛 {@link BusinessException}(50001)，由 {@code GlobalExceptionHandler.handleBusiness}
     * 转 R.fail(50001) 统一响应；调用方（如 moderate）若需要降级，请自行 catch。
     */
    private String chatCompletion(List<Map<String, String>> messages) {
        if (apiKey == null || apiKey.isBlank()) {
            // 不向用户暴露"未配置 API Key"细节，统一脱敏错误
            return AI_UPSTREAM_ERROR_MESSAGE;
        }
        long startNs = System.nanoTime();
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
                        // 仅记录元数据（tenantId/model/耗时/token 计数），不记录 prompt 或 response 文本，避免泄露用户数据。
                        logCallSuccess(startNs, resp.getBody());
                        return (String) message.getOrDefault("content", "");
                    }
                }
            }
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE.getCode(),
                    "AI 服务暂不可用：模型服务没有返回有效内容");
        } catch (RestClientResponseException e) {
            // 安全加固（缺陷 1.18）：不再向客户端回传上游 HTTP 状态码，
            // 避免攻击者借响应区分"401 API Key 无效"、"402 余额不足"、"429 限流"等。
            log.warn("OpenAI API call rejected: status={}", e.getStatusCode().value());
            return AI_UPSTREAM_ERROR_MESSAGE;
        } catch (ResourceAccessException e) {
            log.warn("OpenAI API connection failed: {}", e.getClass().getSimpleName());
            return AI_UPSTREAM_ERROR_MESSAGE;
        } catch (Exception e) {
            log.warn("OpenAI API call failed: {}", e.getClass().getSimpleName());
            return AI_UPSTREAM_ERROR_MESSAGE;
        }
    }

    /** 统一的脱敏错误文案，避免泄漏上游具体错误信息。 */
    private static final String AI_UPSTREAM_ERROR_MESSAGE = "AI 服务暂时不可用，请稍后重试";

    @SuppressWarnings("unchecked")
    private void logCallSuccess(long startNs, Map<String, Object> respBody) {
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
        long promptTokens = -1, completionTokens = -1, totalTokens = -1;
        Object usageObj = respBody.get("usage");
        if (usageObj instanceof Map<?, ?> usage) {
            promptTokens = readNumber(usage.get("prompt_tokens"));
            completionTokens = readNumber(usage.get("completion_tokens"));
            totalTokens = readNumber(usage.get("total_tokens"));
        }
        log.info("AI call done: tenantId={}, model={}, elapsedMs={}, promptTokens={}, completionTokens={}, totalTokens={}",
                TenantContext.getTenantId(), model, elapsedMs, promptTokens, completionTokens, totalTokens);
    }

    private static long readNumber(Object value) {
        return value instanceof Number n ? n.longValue() : -1;
    }

    private static RestTemplate createRestTemplate(Duration connectTimeout, Duration readTimeout) {
        // 使用 SafeHttpClient 防 SSRF / DNS 重绑定：连接阶段二次校验目标 IP，命中私网即终止
        return SafeHttpClient.build((int) connectTimeout.toMillis(), (int) readTimeout.toMillis());
    }

    private static String safeErrorBody(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.length() <= 500 ? value : value.substring(0, 500) + "...";
    }

    private static String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("AI base-url 不能为空");
        }
        String trimmed = value.strip();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
