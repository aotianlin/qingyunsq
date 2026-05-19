package com.campusforum.infra;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.campusforum.tenant.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.Select;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 字典表非租户隔离回归测试：验证 tenants/achievements/sensitive_words 三张表
 * 查询 SQL 不被 TenantLineInnerInterceptor 注入 tenant_id 条件。
 *
 * 实现方式：通过继承 TenantLineInnerInterceptor 暴露 protected 的 processSelect 方法，
 * 直接验证 SQL 改写结果。
 *
 * Validates: Property 7
 */
class TenantIgnoreTablesTest {

    private static final Set<String> TENANT_IGNORE_TABLES = Set.of(
            "tenants", "achievements", "sensitive_words"
    );

    /**
     * 测试用子类，暴露 protected 的 processSelect 方法以便直接验证 SQL 改写行为。
     */
    private static class TestableTenantLineInterceptor extends TenantLineInnerInterceptor {
        TestableTenantLineInterceptor(TenantLineHandler handler) {
            super(handler);
        }

        public void doProcessSelect(Select select) {
            processSelect(select, 0, null, null);
        }
    }

    private TestableTenantLineInterceptor interceptor;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);

        TenantLineHandler handler = new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                Long tenantId = TenantContext.getTenantId();
                if (tenantId == null) {
                    throw new IllegalStateException("TenantContext is null");
                }
                return new LongValue(tenantId);
            }

            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                return TENANT_IGNORE_TABLES.contains(tableName);
            }
        };

        interceptor = new TestableTenantLineInterceptor(handler);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /**
     * 验证 tenants/achievements/sensitive_words 三张表的 SELECT 查询
     * 不被注入 tenant_id 条件。
     */
    @ParameterizedTest(name = "表 {0} 的查询 SQL 不应包含 tenant_id 条件")
    @ValueSource(strings = {"tenants", "achievements", "sensitive_words"})
    void shouldNotInjectTenantIdForIgnoredTables(String tableName) throws Exception {
        String originalSql = "SELECT * FROM " + tableName;
        String processedSql = processSelect(originalSql);

        assertThat(processedSql)
                .as("表 %s 的查询 SQL 不应包含 tenant_id 条件", tableName)
                .doesNotContain("tenant_id");
    }

    /**
     * 验证带 WHERE 条件的查询也不会被注入 tenant_id。
     */
    @ParameterizedTest(name = "表 {0} 带 WHERE 条件的查询不应追加 tenant_id")
    @ValueSource(strings = {"tenants", "achievements", "sensitive_words"})
    void shouldNotInjectTenantIdForIgnoredTablesWithWhereClause(String tableName) throws Exception {
        String originalSql = "SELECT * FROM " + tableName + " WHERE id = 1";
        String processedSql = processSelect(originalSql);

        assertThat(processedSql)
                .as("表 %s 带 WHERE 的查询不应包含 tenant_id", tableName)
                .doesNotContain("tenant_id");
    }

    /**
     * 对比验证：普通业务表（如 posts）的查询 SQL 应被注入 tenant_id 条件。
     */
    @Test
    void shouldInjectTenantIdForRegularBusinessTable() throws Exception {
        String originalSql = "SELECT * FROM posts";
        String processedSql = processSelect(originalSql);

        assertThat(processedSql)
                .as("普通业务表 posts 的查询应包含 tenant_id 条件")
                .contains("tenant_id");
    }

    /**
     * 对比验证：users 表的查询 SQL 应被注入 tenant_id 条件。
     */
    @Test
    void shouldInjectTenantIdForUsersTable() throws Exception {
        String originalSql = "SELECT * FROM users WHERE email = 'test@example.com'";
        String processedSql = processSelect(originalSql);

        assertThat(processedSql)
                .as("普通业务表 users 的查询应包含 tenant_id 条件")
                .contains("tenant_id");
    }

    /**
     * 使用 TenantLineInnerInterceptor 处理 SELECT SQL 并返回改写后的结果。
     */
    private String processSelect(String sql) throws Exception {
        Select select = (Select) CCJSqlParserUtil.parse(sql);
        interceptor.doProcessSelect(select);
        return select.toString();
    }
}
