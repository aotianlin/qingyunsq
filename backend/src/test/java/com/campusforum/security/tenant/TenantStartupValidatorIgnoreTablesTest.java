package com.campusforum.security.tenant;

import com.campusforum.tenant.TenantStartupValidator;
import com.campusforum.tenant.TenantProperties;
import com.campusforum.tenant.mapper.TenantMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link TenantStartupValidator} 的 ignore-tables / business-tables schema 巡检单元测试。
 *
 * <p>对应 bugfix.md 漏洞 14（TENANT_IGNORE_TABLES 巡检缺失：业务表未加 tenant_id 列时静默逃过隔离），
 * 以及 design.md 主题 6 → "目标状态 — TenantStartupValidator 扩展"。</p>
 *
 * <p>实现注意：</p>
 * <ul>
 *   <li>仓库约束禁止使用 H2 / Testcontainers / 新建 docker 容器，所以这里用 Mockito 模拟
 *       {@link DataSource} → {@link Connection} → {@link DatabaseMetaData} → {@link ResultSet} 链路；</li>
 *   <li>为避免触发 standalone/multi 模式校验对 {@code TenantMapper} 的依赖，
 *       这里通过反射直接调用 {@link TenantStartupValidator} 的包内可见方法
 *       {@code validateIgnoreTablesHaveNoTenantId} / {@code validateBusinessTablesHaveTenantId}。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantStartupValidatorIgnoreTablesTest {

    private TenantStartupValidator validator;
    private DataSource dataSource;
    private Connection connection;
    private DatabaseMetaData metaData;

    @BeforeEach
    void setUp() throws Exception {
        TenantProperties props = new TenantProperties();
        TenantMapper mapper = mock(TenantMapper.class);
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        metaData = mock(DatabaseMetaData.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getCatalog()).thenReturn(null);
        when(connection.getMetaData()).thenReturn(metaData);

        validator = new TenantStartupValidator(props, mapper, dataSource);
    }

    @Test
    @DisplayName("忽略名单中的表存在 tenant_id 列时，启动校验抛 IllegalStateException")
    void ignoreTables_with_tenantId_column_throws() throws Exception {
        // 模拟 tenants 表存在 tenant_id 列：getColumns(catalog, null, "tenants", "tenant_id") 返回非空 ResultSet
        ResultSet hasTenantIdRs = mock(ResultSet.class);
        when(hasTenantIdRs.next()).thenReturn(true);
        ResultSet emptyRs = mock(ResultSet.class);
        when(emptyRs.next()).thenReturn(false);

        // 让 tenants 表命中 tenant_id 列；其余两张忽略表（achievements / sensitive_words）不会再被查到就抛错
        when(metaData.getColumns(any(), any(), eq("tenants"), eq("tenant_id")))
                .thenReturn(hasTenantIdRs);
        when(metaData.getColumns(any(), any(), eq("achievements"), eq("tenant_id")))
                .thenReturn(emptyRs);
        when(metaData.getColumns(any(), any(), eq("sensitive_words"), eq("tenant_id")))
                .thenReturn(emptyRs);

        assertThatThrownBy(() -> invokeValidateIgnoreTables(validator))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TENANT_IGNORE_TABLES")
                .hasMessageContaining("tenant_id");
    }

    @Test
    @DisplayName("忽略名单中的表都没有 tenant_id 列时，校验通过")
    void ignoreTables_without_tenantId_column_passes() throws Exception {
        ResultSet emptyRs = mock(ResultSet.class);
        when(emptyRs.next()).thenReturn(false);
        when(metaData.getColumns(any(), any(), any(), eq("tenant_id"))).thenReturn(emptyRs);

        assertThatCode(() -> invokeValidateIgnoreTables(validator))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("业务表缺 tenant_id 列时仅 WARN，不抛错")
    void businessTable_missing_tenantId_logsWarn_butDoesNotThrow() throws Exception {
        // 模拟 schema 中存在一张未在忽略名单中的业务表 "users"，且没有 tenant_id 列
        ResultSet tablesRs = mock(ResultSet.class);
        // next() 第一次返回 true（有一行），第二次返回 false（结束）
        when(tablesRs.next()).thenReturn(true, false);
        when(tablesRs.getString("TABLE_NAME")).thenReturn("users");
        when(metaData.getTables(any(), any(), eq("%"), any())).thenReturn(tablesRs);

        // users.tenant_id 不存在
        ResultSet emptyColRs = mock(ResultSet.class);
        when(emptyColRs.next()).thenReturn(false);
        when(metaData.getColumns(any(), any(), eq("users"), eq("tenant_id"))).thenReturn(emptyColRs);

        // 仅 WARN 不抛错
        assertThatCode(() -> invokeValidateBusinessTables(validator))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("flyway 元数据表与忽略名单内的表都跳过 tenant_id 检查")
    void businessTablesValidator_skips_flyway_and_ignoreTables() throws Exception {
        // schema 中包含 flyway_schema_history 与 tenants（已在忽略名单内）
        ResultSet tablesRs = mock(ResultSet.class);
        when(tablesRs.next()).thenReturn(true, true, false);
        when(tablesRs.getString("TABLE_NAME"))
                .thenReturn("flyway_schema_history", "tenants");
        when(metaData.getTables(any(), any(), eq("%"), any())).thenReturn(tablesRs);

        // 校验内部如果有意外越过 skip 判断会触发对 getColumns(... "tenant_id") 的额外调用，
        // 但这里我们不显式 stub 这两个表的 tenant_id 列查询：只要不抛 NPE 即说明跳过判断生效
        assertThatCode(() -> invokeValidateBusinessTables(validator))
                .doesNotThrowAnyException();
    }

    /** 反射调用包内可见的 validateIgnoreTablesHaveNoTenantId 方法。 */
    private static void invokeValidateIgnoreTables(TenantStartupValidator v) throws Exception {
        Method m = TenantStartupValidator.class.getDeclaredMethod("validateIgnoreTablesHaveNoTenantId");
        m.setAccessible(true);
        try {
            m.invoke(v);
        } catch (InvocationTargetException e) {
            // 把反射包装的异常解包，让 AssertJ 的 hasCauseInstanceOf 能命中底层 IllegalStateException
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }

    /** 反射调用包内可见的 validateBusinessTablesHaveTenantId 方法。 */
    private static void invokeValidateBusinessTables(TenantStartupValidator v) throws Exception {
        Method m = TenantStartupValidator.class.getDeclaredMethod("validateBusinessTablesHaveTenantId");
        m.setAccessible(true);
        try {
            m.invoke(v);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            if (cause instanceof Exception ex) {
                throw ex;
            }
            throw e;
        }
    }
}
