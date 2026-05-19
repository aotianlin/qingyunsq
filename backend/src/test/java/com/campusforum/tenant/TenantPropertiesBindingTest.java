package com.campusforum.tenant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 application.yml 中 tenant.* 前缀的配置能正确绑定到 TenantProperties。
 * 使用独立 profile 避免依赖主配置中的数据库等外部服务。
 */
@SpringBootTest(classes = TenantPropertiesBindingTest.Config.class)
@ActiveProfiles("tenant-binding-test")
class TenantPropertiesBindingTest {

    @EnableConfigurationProperties(TenantProperties.class)
    static class Config {
        // 最小化 Spring 上下文，仅加载配置绑定
    }

    @Autowired
    private TenantProperties tenantProperties;

    @Test
    void shouldBindModeCorrectly() {
        assertThat(tenantProperties.getMode()).isEqualTo(TenantMode.MULTI);
    }

    @Test
    void shouldBindStandaloneTenantId() {
        assertThat(tenantProperties.getStandaloneTenantId()).isEqualTo(42L);
    }

    @Test
    void shouldBindRootDomain() {
        assertThat(tenantProperties.getRootDomain()).isEqualTo("test.example.com");
    }

    @Test
    void shouldBindAllowHeaderFallback() {
        assertThat(tenantProperties.isAllowHeaderFallback()).isFalse();
    }

    @Test
    void shouldBindCacheMaxSize() {
        assertThat(tenantProperties.getCache().getMaxSize()).isEqualTo(512);
    }

    @Test
    void shouldBindCacheTtl() {
        assertThat(tenantProperties.getCache().getTtl()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void shouldHaveDefaultValuesWhenNotOverridden() {
        // 验证 Cache 对象非 null（即使部分字段被覆盖，嵌套对象仍正确初始化）
        assertThat(tenantProperties.getCache()).isNotNull();
    }
}
