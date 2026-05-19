package com.campusforum.test;

import com.campusforum.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 集成测试基类 — 提供共享 MySQL Testcontainer 与租户上下文初始化。
 *
 * <p>子类继承后自动获得：
 * <ul>
 *   <li>共享 MySQLContainer（使用 db/schema.sql 初始化）</li>
 *   <li>@BeforeEach 设置 TenantContext.setTenantId(1L)（模拟 TenantResolutionFilter 行为）</li>
 *   <li>@AfterEach 清理 TenantContext</li>
 * </ul>
 *
 * <p>默认使用 application-test.yml 中的 tenant.mode=standalone 配置。
 * multi 模式测试可通过 @TestPropertySource 覆盖：
 * <pre>
 * {@code @TestPropertySource(properties = {"tenant.mode=multi", "tenant.root-domain=test.local"})}
 * </pre>
 */
@SpringBootTest
@Testcontainers
public abstract class BaseIntegrationTest {

    private static final String MYSQL_IMAGE = "mysql:8.0";
    private static final String INIT_SCRIPT = "schema.sql";

    @Container
    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MYSQL_IMAGE)
            .withDatabaseName("campus_forum")
            .withUsername("test")
            .withPassword("test")
            .withInitScript(INIT_SCRIPT);

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    /**
     * 模拟 TenantResolutionFilter 写入 TenantContext 的行为。
     * standalone 模式下默认设置 tenantId=1。
     * 子类可覆盖此方法以设置不同的租户上下文。
     */
    @BeforeEach
    protected void setTenantContext() {
        TenantContext.setTenantId(1L);
        TenantContext.setTenantCode("default");
    }

    /**
     * 清理 TenantContext，防止 ThreadLocal 泄漏。
     */
    @AfterEach
    protected void clearTenantContext() {
        TenantContext.clear();
    }
}
