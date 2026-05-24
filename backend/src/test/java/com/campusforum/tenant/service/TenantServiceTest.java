package com.campusforum.tenant.service;

import com.campusforum.ai.service.TenantAwareAiService;
import com.campusforum.infra.security.crypto.CryptoService;
import com.campusforum.tenant.domain.Tenant;
import com.campusforum.tenant.mapper.TenantMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TenantService 单测 — 重点验证 updateAiConfig 的 partial-update 语义。
 *
 * 回归保护：F2 让 controller 在脱敏占位符场景下传 apiKey=null，
 * 此时 service 必须保留旧的 apiKey 字段，而不是清空整个 cfg。
 */
@ExtendWith(MockitoExtension.class)
class TenantServiceTest {

    @Mock
    private TenantMapper tenantMapper;

    @Mock
    private CryptoService cryptoService;

    /**
     * security-audit-hardening T7.3 / 漏洞 12 引入：updateAiConfig 在写库后调用
     * {@code tenantAwareAiService.evict(tenantId)} 主动清缓存。本测试 mock 之，
     * 保证调用不会因为 null 引用 NPE。
     */
    @Mock
    private TenantAwareAiService tenantAwareAiService;

    @InjectMocks
    private TenantService tenantService;

    @Test
    void updateAiConfig_shouldPreserveExistingApiKeyWhenNullPassed() {
        // 旧 cfg 已有 apiKey 加密值
        Tenant t = new Tenant();
        t.setId(1L);
        t.setAiConfig("{\"provider\":\"openai\",\"baseUrl\":\"https://api.deepseek.com\","
                + "\"apiKey\":\"OLD_ENCRYPTED_KEY\",\"model\":\"deepseek-v4-pro\"}");
        when(tenantMapper.selectById(1L)).thenReturn(t);

        // controller 传 apiKey=null（表示用户没修改 apiKey）
        tenantService.updateAiConfig(1L, "openai", "https://api.deepseek.com", null, "deepseek-v4-flash");

        ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantMapper).updateById(captor.capture());
        String saved = captor.getValue().getAiConfig();
        // apiKey 必须保留旧加密值
        assertThat(saved).contains("\"apiKey\":\"OLD_ENCRYPTED_KEY\"");
        // 其它字段按传入更新
        assertThat(saved).contains("\"model\":\"deepseek-v4-flash\"");
        assertThat(saved).contains("\"provider\":\"openai\"");
    }

    @Test
    void updateAiConfig_shouldOverwriteApiKeyWhenRealValuePassed() {
        Tenant t = new Tenant();
        t.setId(1L);
        t.setAiConfig("{\"provider\":\"openai\",\"baseUrl\":\"https://api.deepseek.com\","
                + "\"apiKey\":\"OLD_ENCRYPTED_KEY\",\"model\":\"deepseek-v4-pro\"}");
        when(tenantMapper.selectById(1L)).thenReturn(t);
        // encrypt 必须返回新密文（非明文），断言才能验证「不存明文」
        when(cryptoService.encrypt(anyString(), anyString())).thenReturn("NEW_ENCRYPTED_KEY");

        tenantService.updateAiConfig(1L, null, null, "sk-newrealkey1234567890", null);

        ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantMapper).updateById(captor.capture());
        String saved = captor.getValue().getAiConfig();
        // apiKey 已被替换（加密后的密文不会是明文）
        assertThat(saved).doesNotContain("OLD_ENCRYPTED_KEY");
        assertThat(saved).doesNotContain("sk-newrealkey1234567890");  // 必须是加密后的形式
        assertThat(saved).contains("\"apiKey\":\"NEW_ENCRYPTED_KEY\"");
        // 其它字段保留旧值
        assertThat(saved).contains("\"provider\":\"openai\"");
        assertThat(saved).contains("\"model\":\"deepseek-v4-pro\"");
    }

    @Test
    void updateAiConfig_shouldStartFromEmptyWhenLegacyConfigCorrupted() {
        // 旧 cfg 是无效 JSON
        Tenant t = new Tenant();
        t.setId(1L);
        t.setAiConfig("not-a-valid-json{");
        when(tenantMapper.selectById(1L)).thenReturn(t);
        when(cryptoService.encrypt(anyString(), anyString())).thenReturn("ENC_NEW");

        tenantService.updateAiConfig(1L, "openai", "https://api.deepseek.com",
                "sk-test", "deepseek-v4-flash");

        ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantMapper).updateById(captor.capture());
        String saved = captor.getValue().getAiConfig();
        assertThat(saved).contains("\"provider\":\"openai\"");
        assertThat(saved).contains("\"model\":\"deepseek-v4-flash\"");
    }
}
