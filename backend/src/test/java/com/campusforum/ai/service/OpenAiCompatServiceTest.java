package com.campusforum.ai.service;

import com.campusforum.ai.service.AiService.RiskResult;
import com.campusforum.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * OpenAiCompatService 单元测试。
 *
 * 覆盖：
 * - normalizeBaseUrl 不再强制追加 /v1（与 DeepSeek 官方推荐 canonical endpoint 一致）
 * - 空 baseUrl 在构造时 fail-fast 抛 IllegalArgumentException
 * - moderate 在模型回包格式异常时 fail-closed 返回 level=1（疑似），不再静默放行
 * - F4: chatCompletion 失败抛 BusinessException(50001)，调用方可区分调用失败与正常响应
 * - F4: moderate 在上游抛业务异常时仍 fail-closed level=1（不让审核流水被卡死）
 */
@ExtendWith(MockitoExtension.class)
class OpenAiCompatServiceTest {

    @Test
    void normalizeBaseUrl_shouldNotAppendV1() throws Exception {
        Method m = OpenAiCompatService.class.getDeclaredMethod("normalizeBaseUrl", String.class);
        m.setAccessible(true);

        assertThat(m.invoke(null, "https://api.deepseek.com")).isEqualTo("https://api.deepseek.com");
        assertThat(m.invoke(null, "https://api.deepseek.com/")).isEqualTo("https://api.deepseek.com");
        assertThat(m.invoke(null, "https://api.deepseek.com/v1")).isEqualTo("https://api.deepseek.com/v1");
        assertThat(m.invoke(null, " https://api.deepseek.com ")).isEqualTo("https://api.deepseek.com");
    }

    @Test
    void normalizeBaseUrl_shouldFailFastOnBlank() throws Exception {
        Method m = OpenAiCompatService.class.getDeclaredMethod("normalizeBaseUrl", String.class);
        m.setAccessible(true);

        assertThatThrownBy(() -> m.invoke(null, (Object) null))
                .hasCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> m.invoke(null, ""))
                .hasCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> m.invoke(null, "   "))
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_shouldRejectBlankBaseUrl() {
        assertThatThrownBy(() -> new OpenAiCompatService(null, "sk-test", "deepseek-v4-flash"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OpenAiCompatService("", "sk-test", "deepseek-v4-flash"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void moderate_shouldFailClosedOnNonJsonResponse() {
        // 构造 service 后用 mock RestTemplate 替换内部字段，避免真实网络调用。
        OpenAiCompatService service = new OpenAiCompatService(
                "https://api.deepseek.com", "sk-test", "deepseek-v4-flash");
        RestTemplate mockRest = mock(RestTemplate.class);
        ReflectionTestUtils.setField(service, "restTemplate", mockRest);

        // 模拟模型返回非 JSON 文本（例如直接说自然语言而非要求的 JSON）
        Map<String, Object> respBody = Map.of(
                "choices", List.of(Map.of(
                        "message", Map.of("content", "这条内容看起来没问题，应该可以发布。"))));
        when(mockRest.exchange(any(String.class), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(respBody));

        RiskResult result = service.moderate("一些用户内容");

        // fail-closed：解析失败时返回 level=1（疑似），让上层走人工复核
        assertThat(result.level()).isEqualTo(1);
        assertThat(result.reason()).contains("人工复核");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void moderate_shouldParseValidJsonResponse() {
        OpenAiCompatService service = new OpenAiCompatService(
                "https://api.deepseek.com", "sk-test", "deepseek-v4-flash");
        RestTemplate mockRest = mock(RestTemplate.class);
        ReflectionTestUtils.setField(service, "restTemplate", mockRest);

        Map<String, Object> respBody = Map.of(
                "choices", List.of(Map.of(
                        "message", Map.of("content", "{\"level\": 2, \"reason\": \"包含违规内容\"}"))));
        when(mockRest.exchange(any(String.class), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(respBody));

        RiskResult result = service.moderate("test content");

        assertThat(result.level()).isEqualTo(2);
        assertThat(result.reason()).isEqualTo("包含违规内容");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void chat_shouldThrowBusinessExceptionWhenUpstreamRejects() {
        // F4: 上游 4xx/5xx → 抛 50001 BusinessException
        OpenAiCompatService service = new OpenAiCompatService(
                "https://api.deepseek.com", "sk-test", "deepseek-v4-flash");
        RestTemplate mockRest = mock(RestTemplate.class);
        ReflectionTestUtils.setField(service, "restTemplate", mockRest);

        when(mockRest.exchange(any(String.class), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.UNAUTHORIZED, "Unauthorized", null, null, null));

        assertThatThrownBy(() -> service.chat(
                List.of(new AiService.ChatMessage("user", "hi")), null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(50001))
                .hasMessageContaining("AI 服务暂不可用");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void chat_shouldThrowBusinessExceptionWhenUpstreamUnreachable() {
        // F4: 网络层失败 → 抛 50001 BusinessException
        OpenAiCompatService service = new OpenAiCompatService(
                "https://api.deepseek.com", "sk-test", "deepseek-v4-flash");
        RestTemplate mockRest = mock(RestTemplate.class);
        ReflectionTestUtils.setField(service, "restTemplate", mockRest);

        when(mockRest.exchange(any(String.class), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertThatThrownBy(() -> service.chat(
                List.of(new AiService.ChatMessage("user", "hi")), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法连接");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void chat_shouldThrowBusinessExceptionWhenApiKeyMissing() {
        // F4: apiKey 空时立即抛业务异常，不真的发请求
        OpenAiCompatService service = new OpenAiCompatService(
                "https://api.deepseek.com", "", "deepseek-v4-flash");

        assertThatThrownBy(() -> service.chat(
                List.of(new AiService.ChatMessage("user", "hi")), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未配置 API Key");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void moderate_shouldFailClosedWhenUpstreamThrows() {
        // F4: moderate 必须不暴露上游业务异常给调用方，保持 fail-closed 语义
        OpenAiCompatService service = new OpenAiCompatService(
                "https://api.deepseek.com", "sk-test", "deepseek-v4-flash");
        RestTemplate mockRest = mock(RestTemplate.class);
        ReflectionTestUtils.setField(service, "restTemplate", mockRest);

        when(mockRest.exchange(any(String.class), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        // 注意：不应抛异常，而是返回 level=1 让审核流走人工复核
        RiskResult result = service.moderate("可疑内容");
        assertThat(result.level()).isEqualTo(1);
        assertThat(result.reason()).contains("人工复核");
    }
}
