package com.campusforum.test;

import com.campusforum.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * 集成测试基类 — 提供共享 MySQL 数据源接入与租户上下文初始化。
 *
 * <p>数据源选择策略（按优先级）：</p>
 * <ol>
 *   <li><b>CI 环境</b>：当 {@code SPRING_PROFILES_ACTIVE=ci} 时跳过本地容器，
 *       直接使用 CI service containers（由 {@code application-ci.yml} 注入）。</li>
 *   <li><b>显式开启 Testcontainers</b>：当 {@code USE_TESTCONTAINERS=true} 时
 *       启动一个临时 MySQL 容器（仅供没有可用宿主机数据库时使用）。</li>
 *   <li><b>默认</b>：连接已经在虚拟机 {@code 192.168.150.130:3306} 上跑着的
 *       Docker MySQL 容器。账号 {@code root} / 密码 {@code 123456}（与
 *       {@code application-dev.yml} 默认值一致）。该模式由
 *       {@link #configureRemoteVm(DynamicPropertyRegistry)} 注入连接信息。
 *       好处：不在本地起任何容器、复用已有历史测试数据、与开发态行为对齐。</li>
 * </ol>
 *
 * <p>注意：默认模式下集成测试会真实读写 {@code campus_forum} 库，请确保单测
 * 不会污染历史数据；如需隔离请通过 ENV 覆盖 {@code TEST_MYSQL_DATABASE} 指向
 * 专用测试库。</p>
 */
@SpringBootTest
public abstract class BaseIntegrationTest {

    /** Testcontainers 默认镜像，仅在 USE_TESTCONTAINERS=true 时使用。 */
    private static final String MYSQL_IMAGE = "mysql:8.0";

    /** Testcontainers 启动时执行的 schema 初始化脚本（位于 test/resources）。 */
    private static final String INIT_SCRIPT = "schema.sql";

    /** 当前是否运行在 CI（GitHub Actions）service container 模式。 */
    private static final boolean IS_CI = "ci".equals(System.getenv("SPRING_PROFILES_ACTIVE"));

    /** 是否显式启用 Testcontainers；缺省 false，避免在本机自动起容器。 */
    private static final boolean USE_TESTCONTAINERS =
            "true".equalsIgnoreCase(System.getenv("USE_TESTCONTAINERS"));

    /** 虚拟机模式下的 MySQL 主机；可通过 ENV 覆盖以适配其他开发机。 */
    private static final String VM_HOST =
            System.getenv().getOrDefault("TEST_MYSQL_HOST", "192.168.150.130");

    /** 虚拟机模式下的 MySQL 端口。 */
    private static final String VM_PORT =
            System.getenv().getOrDefault("TEST_MYSQL_PORT", "3306");

    /** 虚拟机模式下的 MySQL 数据库名。 */
    private static final String VM_DATABASE =
            System.getenv().getOrDefault("TEST_MYSQL_DATABASE", "campus_forum");

    /** 虚拟机模式下的 MySQL 用户名。 */
    private static final String VM_USERNAME =
            System.getenv().getOrDefault("TEST_MYSQL_USERNAME", "root");

    /** 虚拟机模式下的 MySQL 密码。 */
    private static final String VM_PASSWORD =
            System.getenv().getOrDefault("TEST_MYSQL_PASSWORD", "123456");

    /** Testcontainers 实例；仅在 USE_TESTCONTAINERS=true 时被创建。 */
    private static final MySQLContainer<?> MYSQL;

    static {
        if (!IS_CI && USE_TESTCONTAINERS) {
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

    /**
     * 注入数据源连接参数。三种模式互斥：
     * <ul>
     *   <li>CI 模式 → 不注入，由 {@code application-ci.yml} 提供。</li>
     *   <li>Testcontainers 模式 → 注入容器动态分配的 jdbc url / 账号 / 密码。</li>
     *   <li>虚拟机模式（默认）→ 注入 {@code 192.168.150.130:3306} 的连接信息。</li>
     * </ul>
     */
    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        if (MYSQL != null) {
            // 模式 2：Testcontainers
            registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
            registry.add("spring.datasource.username", MYSQL::getUsername);
            registry.add("spring.datasource.password", MYSQL::getPassword);
            registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        } else if (!IS_CI) {
            // 模式 3（默认）：连接虚拟机 192.168.150.130 上已运行的 MySQL 容器
            configureRemoteVm(registry);
        }
        // 模式 1（IS_CI=true）：留给 application-ci.yml 自行注入
    }

    /**
     * 将默认数据源指向虚拟机内已经运行的 MySQL 容器。
     *
     * @param registry Spring 测试动态属性注册表
     */
    private static void configureRemoteVm(DynamicPropertyRegistry registry) {
        String jdbcUrl = "jdbc:mysql://" + VM_HOST + ":" + VM_PORT + "/" + VM_DATABASE
                + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false";
        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", () -> VM_USERNAME);
        registry.add("spring.datasource.password", () -> VM_PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        // Redis 同样指向虚拟机；密码保持与 dev 一致（123456）
        registry.add("spring.data.redis.host", () -> VM_HOST);
        registry.add("spring.data.redis.port", () -> "6379");
        registry.add("spring.data.redis.password",
                () -> System.getenv().getOrDefault("TEST_REDIS_PASSWORD", "123456"));
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
