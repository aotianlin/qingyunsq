package com.campusforum.ai.service;

import com.campusforum.ai.config.AiHttpProperties;
import com.campusforum.tenant.TenantContext;
import com.campusforum.tenant.service.TenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TenantAwareAiService 单元测试。
 *
 * 覆盖：
 * - 同一份配置两次 delegate 命中缓存，返回同一个 OpenAiCompatService 实例（验证 P4 缓存生效）
 * - 配置变更后 cache key 变 → 自动 cache miss 返回新实例（验证热生效不被缓存破坏）
 * - baseUrl 为空时 fallback mock（验证 P1 上游校验）
 */
@ExtendWith(MockitoExtension.class)
class TenantAwareAiServiceTest {

    @Mock
    private TenantService tenantService;

    @Mock
    private MockAiService mockAiService;

    private final AiHttpProperties httpProperties = new AiHttpProperties();  // 用默认 8s/30s

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void delegate_shouldReturnCachedClientForSameConfig() throws Exception {
        TenantAwareAiService svc = new TenantAwareAiService(tenantService, mockAiService, httpProperties);
        TenantContext.setTenantId(1L);

        when(tenantService.getAiConfig(1L)).thenReturn(Map.of(
                "provider", "openai",
                "baseUrl", "https://api.deepseek.com",
                "apiKey", "sk-test-key",
                "model", "deepseek-v4-flash"
        ));

        Object first = invokeDelegate(svc);
        Object second = invokeDelegate(svc);

        assertThat(first)
                .isInstanceOf(OpenAiCompatService.class)
                .isSameAs(second);
        verify(tenantService, times(2)).getAiConfig(1L);
    }

    @Test
    void delegate_shouldReturnNewClientWhenConfigChanges() throws Exception {
        TenantAwareAiService svc = new TenantAwareAiService(tenantService, mockAiService, httpProperties);
        TenantContext.setTenantId(1L);

        when(tenantService.getAiConfig(1L))
                .thenReturn(Map.of(
                        "provider", "openai",
                        "baseUrl", "https://api.deepseek.com",
                        "apiKey", "sk-old",
                        "model", "deepseek-v4-flash"))
                .thenReturn(Map.of(
                        "provider", "openai",
                        "baseUrl", "https://api.deepseek.com",
                        "apiKey", "sk-new",   // apiKey 变更
                        "model", "deepseek-v4-flash"));

        Object first = invokeDelegate(svc);
        Object second = invokeDelegate(svc);

        assertThat(first).isInstanceOf(OpenAiCompatService.class);
        assertThat(second).isInstanceOf(OpenAiCompatService.class);
        assertThat(first).isNotSameAs(second);
    }

    @Test
    void delegate_shouldFallbackToMockWhenBaseUrlBlank() throws Exception {
        TenantAwareAiService svc = new TenantAwareAiService(tenantService, mockAiService, httpProperties);
        TenantContext.setTenantId(1L);

        when(tenantService.getAiConfig(1L)).thenReturn(Map.of(
                "provider", "openai",
                "baseUrl", "",            // 缺 baseUrl
                "apiKey", "sk-test",
                "model", "deepseek-v4-flash"));

        Object result = invokeDelegate(svc);
        assertThat(result).isSameAs(mockAiService);
    }

    @Test
    void delegate_shouldFallbackToMockWhenApiKeyBlank() throws Exception {
        TenantAwareAiService svc = new TenantAwareAiService(tenantService, mockAiService, httpProperties);
        TenantContext.setTenantId(1L);

        when(tenantService.getAiConfig(1L)).thenReturn(Map.of(
                "provider", "openai",
                "baseUrl", "https://api.deepseek.com",
                "apiKey", "",             // 缺 apiKey
                "model", "deepseek-v4-flash"));

        Object result = invokeDelegate(svc);
        assertThat(result).isSameAs(mockAiService);
    }

    @Test
    void delegate_shouldFallbackToMockWhenTenantContextMissing() throws Exception {
        TenantAwareAiService svc = new TenantAwareAiService(tenantService, mockAiService, httpProperties);
        // 不设置 TenantContext

        Object result = invokeDelegate(svc);
        assertThat(result).isSameAs(mockAiService);
    }

    private Object invokeDelegate(TenantAwareAiService svc) throws Exception {
        Method m = TenantAwareAiService.class.getDeclaredMethod("delegate");
        m.setAccessible(true);
        return m.invoke(svc);
    }
}
