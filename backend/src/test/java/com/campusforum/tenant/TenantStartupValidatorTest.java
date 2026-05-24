package com.campusforum.tenant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.tenant.domain.Tenant;
import com.campusforum.tenant.mapper.TenantMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.boot.DefaultApplicationArguments;

import javax.sql.DataSource;
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
 * TenantStartupValidator 单元测试。
 * 验证 standalone/multi 两种模式下的启动期校验逻辑。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantStartupValidatorTest {

    @Mock
    private TenantMapper tenantMapper;

    private TenantProperties props;
    private DataSource dataSource;
    private TenantStartupValidator validator;

    @BeforeEach
    void setUp() throws Exception {
        props = new TenantProperties();
        // 默认提供一个"什么列都没有"的 DataSource mock，让 standalone/multi 通过后的
        // ignore-tables 校验也能跑过（不会抛错）
        dataSource = mockEmptyDataSource();
        validator = new TenantStartupValidator(props, tenantMapper, dataSource);
    }

    /**
     * 构造一个返回"忽略名单中所有表都没有 tenant_id 列、且 schema 中无任何业务表"的 DataSource mock，
     * 让 schema 校验环节直接通过，便于复用既有的 standalone/multi 用例。
     */
    private DataSource mockEmptyDataSource() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        DatabaseMetaData md = mock(DatabaseMetaData.class);
        ResultSet emptyRs = mock(ResultSet.class);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.getCatalog()).thenReturn(null);
        when(conn.getMetaData()).thenReturn(md);
        // getColumns 与 getTables 都返回空 ResultSet（next() = false）
        when(md.getColumns(any(), any(), any(), any())).thenReturn(emptyRs);
        when(md.getTables(any(), any(), any(), any())).thenReturn(emptyRs);
        when(emptyRs.next()).thenReturn(false);
        return ds;
    }

    @Nested
    @DisplayName("Standalone 模式")
    class StandaloneMode {

        @BeforeEach
        void setStandaloneMode() {
            props.setMode(TenantMode.STANDALONE);
            props.setStandaloneTenantId(1L);
        }

        @Test
        @DisplayName("tenants 表中不存在 id=1 的记录时，启动失败抛 IllegalStateException")
        void shouldFailWhenTenantNotFound() {
            when(tenantMapper.selectById(1L)).thenReturn(null);

            assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Standalone mode requires tenants table to contain id=1 with status=1")
                    .hasMessageContaining("Run the bootstrap migration first.");
        }

        @Test
        @DisplayName("tenants 表中 id=1 存在但 status 为 null 时，启动失败")
        void shouldFailWhenTenantStatusIsNull() {
            Tenant tenant = new Tenant();
            tenant.setId(1L);
            tenant.setStatus(null);
            when(tenantMapper.selectById(1L)).thenReturn(tenant);

            assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Standalone mode requires tenants table to contain id=1 with status=1");
        }

        @Test
        @DisplayName("tenants 表中 id=1 存在但 status != 1 时，启动失败")
        void shouldFailWhenTenantStatusIsNotActive() {
            Tenant tenant = new Tenant();
            tenant.setId(1L);
            tenant.setStatus(0);
            when(tenantMapper.selectById(1L)).thenReturn(tenant);

            assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Standalone mode requires tenants table to contain id=1 with status=1");
        }

        @Test
        @DisplayName("tenants 表中 id=1 存在且 status=1 时，启动成功")
        void shouldPassWhenTenantExistsAndActive() {
            Tenant tenant = new Tenant();
            tenant.setId(1L);
            tenant.setStatus(1);
            when(tenantMapper.selectById(1L)).thenReturn(tenant);

            assertThatCode(() -> validator.run(new DefaultApplicationArguments()))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("自定义 standaloneTenantId 时校验对应 id")
        void shouldValidateCustomStandaloneTenantId() {
            props.setStandaloneTenantId(42L);
            when(tenantMapper.selectById(42L)).thenReturn(null);

            assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("id=42");
        }
    }

    @Nested
    @DisplayName("Multi 模式")
    class MultiMode {

        @BeforeEach
        void setMultiMode() {
            props.setMode(TenantMode.MULTI);
            props.setRootDomain("campusforum.com");
        }

        @Test
        @DisplayName("无 active 租户时，启动失败")
        void shouldFailWhenNoActiveTenants() {
            when(tenantMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Multi mode requires at least one active tenant.");
        }

        @Test
        @DisplayName("有 active 租户但 rootDomain 为 null 时，启动失败")
        void shouldFailWhenRootDomainIsNull() {
            props.setRootDomain(null);
            when(tenantMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

            assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Multi mode requires tenant.root-domain to be set.");
        }

        @Test
        @DisplayName("有 active 租户但 rootDomain 为空白字符串时，启动失败")
        void shouldFailWhenRootDomainIsBlank() {
            props.setRootDomain("   ");
            when(tenantMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

            assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Multi mode requires tenant.root-domain to be set.");
        }

        @Test
        @DisplayName("有 active 租户且 rootDomain 配置正确时，启动成功")
        void shouldPassWhenActiveTenantsExistAndRootDomainSet() {
            when(tenantMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);

            assertThatCode(() -> validator.run(new DefaultApplicationArguments()))
                    .doesNotThrowAnyException();
        }
    }
}
