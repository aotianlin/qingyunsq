package com.campusforum.ai.service;

import com.campusforum.ai.dto.AiResponse;
import com.campusforum.search.dto.SearchResultVO;
import com.campusforum.search.service.SearchService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagChatServiceTest {

    @Test
    void shouldRetrieveSourcesWithSimplifiedQuestionAndPassContextToAi() {
        StubAiService aiService = new StubAiService("已根据资料回答");
        StubSearchService searchService = new StubSearchService();
        RagChatService ragChatService = new RagChatService(aiService, searchService);

        SearchResultVO resource = SearchResultVO.builder()
                .type("RESOURCE")
                .id(12L)
                .title("红黑树复习资料.md")
                .description("数据结构课程中的红黑树旋转和插入修复笔记")
                .build();
        searchService.addResult("红黑树", "RESOURCE", resource);

        AiResponse response = ragChatService.chat(
                List.of(new AiService.ChatMessage("user", "有没有红黑树资料？")),
                "用户已绑定帖子上下文"
        );

        assertThat(response.getReply()).isEqualTo("已根据资料回答");
        assertThat(response.getCitations()).hasSize(1);
        assertThat(response.getCitations().get(0).getUrl()).isEqualTo("/resources/12");
        assertThat(aiService.lastContext)
                .contains("用户已绑定帖子上下文")
                .contains("红黑树复习资料.md")
                .contains("参考来源");
    }

    @Test
    void shouldSkipRetrievalWhenQuestionIsBlank() {
        StubAiService aiService = new StubAiService("默认回答");
        StubSearchService searchService = new StubSearchService();
        RagChatService ragChatService = new RagChatService(aiService, searchService);

        AiResponse response = ragChatService.chat(
                List.of(new AiService.ChatMessage("user", "   ")),
                null
        );

        assertThat(response.getReply()).isEqualTo("默认回答");
        assertThat(response.getCitations()).isEmpty();
        assertThat(searchService.callCount).isZero();
    }

    private static class StubAiService implements AiService {
        private final String reply;
        private String lastContext;

        private StubAiService(String reply) {
            this.reply = reply;
        }

        @Override
        public String summarize(String content) {
            return "";
        }

        @Override
        public RiskResult moderate(String content) {
            return new RiskResult(0, "");
        }

        @Override
        public List<String> recommendTags(String title, String content) {
            return List.of();
        }

        @Override
        public String chat(List<ChatMessage> messages, String context) {
            lastContext = context;
            return reply;
        }
    }

    private static class StubSearchService extends SearchService {
        private final Map<String, List<SearchResultVO>> results = new HashMap<>();
        private int callCount;

        private StubSearchService() {
            super(null, null, null, null, null);
        }

        private void addResult(String keyword, String type, SearchResultVO result) {
            results.put(key(keyword, type), List.of(result));
        }

        @Override
        public List<SearchResultVO> search(String keyword, String type, String sort, Long cursor, int limit) {
            callCount++;
            return results.getOrDefault(key(keyword, type), List.of());
        }

        private String key(String keyword, String type) {
            return keyword + ":" + type;
        }
    }
}
