package com.campusforum.ai.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class AiServiceTest {

    @Autowired
    private AiService aiService;

    @Test
    void shouldSummarizeShortContent() {
        String result = aiService.summarize("这是一个简短的测试文本。");
        assertThat(result).isEqualTo("这是一个简短的测试文本。");
    }

    @Test
    void shouldTruncateLongContent() {
        String longContent = "A".repeat(200);
        String result = aiService.summarize(longContent);
        assertThat(result).endsWith("...");
        assertThat(result.length()).isLessThanOrEqualTo(153); // 150 + "..."
    }

    @Test
    void shouldReturnEmptyForBlankContent() {
        assertThat(aiService.summarize("")).isEmpty();
        assertThat(aiService.summarize(null)).isEmpty();
    }

    @Test
    void shouldDetectRiskKeyword() {
        AiService.RiskResult result = aiService.moderate("代考四六级，价格优惠");
        assertThat(result.level()).isEqualTo(2);
        assertThat(result.reason()).contains("代考");
    }

    @Test
    void shouldPassCleanContent() {
        AiService.RiskResult result = aiService.moderate("数据结构考试复习资料分享");
        assertThat(result.level()).isEqualTo(0);
    }

    @Test
    void shouldRecommendTagsFromContent() {
        List<String> tags = aiService.recommendTags("红黑树旋转问题", "数据结构课程中红黑树旋转的实现方法");
        assertThat(tags).isNotEmpty();
        assertThat(tags).anyMatch(t -> t.contains("红黑") || t.contains("旋转") || t.contains("数据结构"));
    }

    @Test
    void shouldReturnEmptyTagsForBlankInput() {
        assertThat(aiService.recommendTags("", "")).isEmpty();
        assertThat(aiService.recommendTags(null, null)).isEmpty();
    }

    @Test
    void shouldRespondToChat() {
        String reply = aiService.chat(
                List.of(new AiService.ChatMessage("user", "如何发帖？")),
                null
        );
        assertThat(reply).isNotEmpty();
        assertThat(reply).contains("发帖");
    }

    @Test
    void shouldReturnDefaultGreetingForEmptyMessages() {
        String reply = aiService.chat(List.of(), null);
        assertThat(reply).contains("小青").contains("青云阁");
    }
}
