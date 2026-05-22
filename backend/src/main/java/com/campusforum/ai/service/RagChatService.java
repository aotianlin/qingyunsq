package com.campusforum.ai.service;

import com.campusforum.ai.dto.AiCitation;
import com.campusforum.ai.dto.AiResponse;
import com.campusforum.search.dto.SearchResultVO;
import com.campusforum.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RagChatService {

    private static final int MAX_CONTEXT_LENGTH = 3200;
    private static final int MAX_CITATIONS = 6;
    private static final int MAX_QUERY_CANDIDATES = 5;
    private static final Pattern QUERY_TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}A-Za-z0-9]{2,}");
    private static final List<String> QUERY_STOP_FRAGMENTS = List.of(
            "请问", "帮我", "我想", "能不能", "可以", "一下", "有没有", "有哪些",
            "哪里", "什么", "怎么", "如何", "相关", "推荐", "查找", "搜索",
            "资源", "资料", "帖子", "内容", "学习圈", "空间", "课程", "这个", "那个",
            "的吗", "呢", "吗", "的"
    );

    private final AiService aiService;
    private final SearchService searchService;

    public AiResponse chat(List<AiService.ChatMessage> messages, String manualContext) {
        String question = latestUserQuestion(messages);
        List<AiCitation> citations = retrieve(question);
        String ragContext = buildContext(manualContext, citations);
        String reply = aiService.chat(messages, ragContext);

        return AiResponse.builder()
                .reply(reply)
                .citations(citations)
                .build();
    }

    private String latestUserQuestion(List<AiService.ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        for (int i = messages.size() - 1; i >= 0; i--) {
            AiService.ChatMessage message = messages.get(i);
            if ("user".equalsIgnoreCase(message.role()) && message.content() != null) {
                return normalizeQuery(message.content());
            }
        }
        return "";
    }

    private List<AiCitation> retrieve(String question) {
        if (question.isBlank()) {
            return List.of();
        }

        Map<String, AiCitation> unique = new LinkedHashMap<>();
        // 用户提问通常是自然语言整句，直接拿整句做 LIKE/FULLTEXT 容易漏召回。
        // 这里先生成少量稳定检索词，再按站内内容类型检索并去重，避免为了 RAG 引入额外索引系统。
        for (String query : buildQueryCandidates(question)) {
            for (String type : List.of("POST", "RESOURCE", "SPACE")) {
                for (SearchResultVO result : searchService.search(query, type, "time", null, 4)) {
                    if (unique.size() >= MAX_CITATIONS) {
                        return new ArrayList<>(unique.values());
                    }
                    AiCitation citation = toCitation(result);
                    unique.putIfAbsent(citation.getType() + ":" + citation.getId(), citation);
                }
            }
        }
        return new ArrayList<>(unique.values());
    }

    private AiCitation toCitation(SearchResultVO result) {
        String type = result.getType();
        return AiCitation.builder()
                .type(type)
                .id(result.getId())
                .title(blankToDefault(result.getTitle(), type + " #" + result.getId()))
                .snippet(truncate(blankToDefault(result.getDescription(), "无摘要"), 220))
                .url(urlFor(type, result.getId()))
                .build();
    }

    private String buildContext(String manualContext, List<AiCitation> citations) {
        StringBuilder context = new StringBuilder();
        context.append("你是 CampusForum 的检索增强问答助手。回答必须优先依据【用户提供上下文】和【站内检索资料】。")
                .append("如果资料不足，请明确说明无法从现有资料确认，不要编造。回答末尾用“参考来源”列出用到的编号。\n\n");

        if (manualContext != null && !manualContext.isBlank()) {
            context.append("【用户提供上下文】\n")
                    .append(truncate(manualContext, 1200))
                    .append("\n\n");
        }

        if (citations.isEmpty()) {
            context.append("【站内检索资料】\n未召回到相关资料。\n");
        } else {
            context.append("【站内检索资料】\n");
            for (int i = 0; i < citations.size(); i++) {
                AiCitation citation = citations.get(i);
                context.append("[")
                        .append(i + 1)
                        .append("] ")
                        .append(citation.getType())
                        .append(" ")
                        .append(citation.getTitle())
                        .append("\n摘要：")
                        .append(citation.getSnippet())
                        .append("\n链接：")
                        .append(citation.getUrl())
                        .append("\n\n");
            }
        }

        return truncate(context.toString(), MAX_CONTEXT_LENGTH);
    }

    private List<String> buildQueryCandidates(String question) {
        String normalized = normalizeQuery(question);
        if (normalized.isBlank()) {
            return List.of();
        }

        Set<String> queries = new LinkedHashSet<>();
        addQuery(queries, normalized);

        String simplified = simplifyQuestion(normalized);
        addQuery(queries, simplified);

        for (String token : simplified.split("\\s+")) {
            addQuery(queries, token);
        }

        // 中文问题没有天然空格，补充前后缀短词可以覆盖“红黑树旋转”“数据结构复习资料”这类检索场景。
        Matcher matcher = QUERY_TOKEN_PATTERN.matcher(simplified.replaceAll("\\s+", ""));
        while (matcher.find()) {
            String token = matcher.group();
            addQuery(queries, token);
            if (token.length() > 3) {
                addQuery(queries, token.substring(0, Math.min(4, token.length())));
                addQuery(queries, token.substring(0, Math.min(3, token.length())));
                addQuery(queries, token.substring(Math.max(0, token.length() - 4)));
                addQuery(queries, token.substring(Math.max(0, token.length() - 3)));
            }
            if (queries.size() >= MAX_QUERY_CANDIDATES) {
                break;
            }
        }

        return queries.stream().limit(MAX_QUERY_CANDIDATES).toList();
    }

    private void addQuery(Set<String> queries, String value) {
        String query = normalizeQuery(value);
        if (query.length() >= 2) {
            queries.add(query);
        }
    }

    private String simplifyQuestion(String value) {
        String simplified = value;
        for (String fragment : QUERY_STOP_FRAGMENTS) {
            simplified = simplified.replace(fragment, " ");
        }
        return simplified.replaceAll("\\s+", " ").strip();
    }

    private String normalizeQuery(String value) {
        return value.replaceAll("[^\\p{L}\\p{N}\\s]", " ").replaceAll("\\s+", " ").strip();
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        return value.length() <= limit ? value : value.substring(0, limit) + "...";
    }

    private String urlFor(String type, Long id) {
        if (id == null) {
            return "";
        }
        return switch (type) {
            case "POST" -> "/posts/" + id;
            case "RESOURCE" -> "/resources/" + id;
            case "SPACE" -> "/spaces/" + id;
            default -> "";
        };
    }
}
