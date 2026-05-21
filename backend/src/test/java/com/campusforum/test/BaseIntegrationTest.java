package com.campusforum.test;

import com.campusforum.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * 集成测试基类 — 提供共享 MySQL Testcontainer 与租户上下文初始化。
 *
 * <p>CI 环境中（SPRING_PROFILES_ACTIVE=ci），跳过 Testcontainers，
 * 直接使用 CI service containers 提供的 MySQL（由 application-ci.yml 配置）。
 *
 * <p>本地开发环境中，自动启动 Testcontainers MySQL 容器。
 */
@SpringBootTest
public abstract class BaseIntegrationTest {

    private static final String MYSQL_IMAGE = "mysql:8.0";
    private static final String INIT_SCRIPT = "schema.sql";
    private static final boolean IS_CI = "ci".equals(System.getenv("SPRING_PROFILES_ACTIVE"));

    private static final MySQLContainer<?> MYSQL;

    static {
        if (!IS_CI) {
            MYSQL = new MySQLContainer<>(MYSQL_IMAGE)
                    .withDatabaseName("campus_forum")
                    .withUsername("test")
                    .withPassword("test")
                    .withInitScript(INIT_SCRIPT);
            MYSQL.start();
        } else {
            MYSQL = null;
        }
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        if (MYSQL != null) {
            registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
            registry.add("spring.datasource.username", MYSQL::getUsername);
            registry.add("spring.datasource.password", MYSQL::getPassword);
            registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        }
    }

    @BeforeEach
    protected void setTenantContext() {
        TenantContext.setTenantId(1L);
        TenantContext.setTenantCode("default");
    }

    @AfterEach
    protected void clearTenantContext() {
        TenantContext.clear();
    }
}
