package com.campusforum.tenant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.infra.MyBatisPlusConfig;
import com.campusforum.tenant.domain.Tenant;
import com.campusforum.tenant.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

/**
 * 启动期租户校验器。
 *
 * <p>standalone 模式：检查 tenants 表存在 (id=standaloneTenantId, status=1)。
 * 若不存在，启动失败并打印明确错误。</p>
 *
 * <p>multi 模式：检查 tenants 表至少有 1 条 status=1 记录，且 root-domain 配置非空。</p>
 *
 * <p>除了上述两种模式各自的基础校验外，本类还会在启动期对 schema 与
 * {@link MyBatisPlusConfig#TENANT_IGNORE_TABLES} 做交叉巡检，对应
 * bugfix.md 漏洞 14（TENANT_IGNORE_TABLES 巡检缺失：业务表未加 tenant_id 列时静默逃过隔离）：</p>
 * <ul>
 *   <li>{@link #validateIgnoreTablesHaveNoTenantId()}：忽略名单内的表如果实际仍有 {@code tenant_id} 列，
 *       说明这是应当做租户隔离但被误加入忽略名单的业务表，启动期直接抛 {@link IllegalStateException} 阻断；</li>
 *   <li>{@link #validateBusinessTablesHaveTenantId()}：枚举 schema 中所有非忽略 / 非 flyway 元数据表，
 *       若缺少 {@code tenant_id} 列仅 WARN 不阻断，避免本地缺表 / 旧版本迁移让启动失败，
 *       由开发审视是否应加入 {@link MyBatisPlusConfig#TENANT_IGNORE_TABLES}。</li>
 * </ul>
 *
 * <p>schema 巡检完全依赖 JDBC 元数据 API（{@link DatabaseMetaData#getColumns} /
 * {@link DatabaseMetaData#getTables}），不需要 SQL 写权限，也不会改动任何业务数据。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantStartupValidator implements ApplicationRunner {

    private final TenantProperties props;
    private final TenantMapper tenantMapper;
    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 先做基础的 standalone / multi 模式校验，保留既有语义
        if (props.getMode() == TenantMode.STANDALONE) {
            validateStandaloneMode();
        } else {
            validateMultiMode();
        }
        // 然后做 schema 与 ignore-tables 的交叉巡检（漏洞 14）
        validateIgnoreTablesHaveNoTenantId();
        validateBusinessTablesHaveTenantId();
    }

    private void validateStandaloneMode() {
        Tenant t = tenantMapper.selectById(props.getStandaloneTenantId());
        if (t == null || t.getStatus() == null || t.getStatus() != 1) {
            throw new IllegalStateException(
                    "Standalone mode requires tenants table to contain id="
                            + props.getStandaloneTenantId() + " with status=1. "
                            + "Run the bootstrap migration first.");
        }
        log.info("TenantStartupValidator: standalone mode validated, tenantId={}",
                props.getStandaloneTenantId());
    }

    private void validateMultiMode() {
        long activeCount = tenantMapper.selectCount(
                new LambdaQueryWrapper<Tenant>().eq(Tenant::getStatus, 1));
        if (activeCount == 0) {
            throw new IllegalStateException("Multi mode requires at least one active tenant.");
        }
        if (props.getRootDomain() == null || props.getRootDomain().isBlank()) {
            throw new IllegalStateException("Multi mode requires tenant.root-domain to be set.");
        }
        log.info("TenantStartupValidator: multi mode validated, activeCount={}, rootDomain={}",
                activeCount, props.getRootDomain());
    }

    /**
     * 校验 {@link MyBatisPlusConfig#TENANT_IGNORE_TABLES} 中的表确实不含 {@code tenant_id} 列。
     *
     * <p>若发现忽略名单内的表实际仍带有 {@code tenant_id} 列，意味着该表本应受租户隔离但被误加入忽略名单，
     * MyBatis-Plus 的 {@code TenantLineInnerInterceptor} 不会再为它注入 {@code tenant_id = ?} 条件，
     * 导致 TENANT_ADMIN 等高权角色可能读到全租户数据。此处直接抛 {@link IllegalStateException}
     * 阻断启动，强制开发先确认该表的隔离策略（对应 bugfix.md 漏洞 14）。</p>
     */
    void validateIgnoreTablesHaveNoTenantId() throws SQLException {
        Set<String> ignoreTables = MyBatisPlusConfig.TENANT_IGNORE_TABLES;
        try (Connection conn = dataSource.getConnection()) {
            for (String table : ignoreTables) {
                if (hasColumn(conn, table, "tenant_id")) {
                    throw new IllegalStateException(
                            "表 " + table + " 在 TENANT_IGNORE_TABLES 中但仍有 tenant_id 列，"
                                    + "请确认是否需要租户隔离");
                }
            }
        }
        log.info("TenantStartupValidator: ignore-tables 校验通过, tables={}", ignoreTables);
    }

    /**
     * 反向枚举 schema 中所有非忽略 / 非 flyway 元数据表，若缺少 {@code tenant_id} 列则 WARN 提示。
     *
     * <p>仅 WARN 不抛错的原因：本地开发或 CI 环境可能缺少部分业务表（例如某迁移尚未执行），
     * 此时启动失败会让本地 / 测试体验非常糟糕。该提示用于开发期审视："这是否是一张应受租户隔离的业务表？
     * 如果不是，请加入 {@link MyBatisPlusConfig#TENANT_IGNORE_TABLES}；如果是，请补 tenant_id 列。"</p>
     *
     * <p>跳过的表前缀：</p>
     * <ul>
     *   <li>{@code flyway_*}：Flyway 自身的迁移历史表；</li>
     *   <li>{@code schema_*}：数据库 schema 元数据 / 历史表（含 schema_version 等）。</li>
     * </ul>
     */
    void validateBusinessTablesHaveTenantId() throws SQLException {
        Set<String> ignoreTables = MyBatisPlusConfig.TENANT_IGNORE_TABLES;
        try (Connection conn = dataSource.getConnection();
             ResultSet rs = conn.getMetaData().getTables(
                     conn.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String table = rs.getString("TABLE_NAME");
                if (table == null) {
                    continue;
                }
                String lower = table.toLowerCase();
                if (ignoreTables.contains(lower)
                        || lower.startsWith("flyway_")
                        || lower.startsWith("schema_")) {
                    continue;
                }
                if (!hasColumn(conn, table, "tenant_id")) {
                    log.warn("表 {} 没有 tenant_id 列，请确认是否应加入 TENANT_IGNORE_TABLES",
                            table);
                }
            }
        }
    }

    /**
     * 判断指定表是否存在指定列。基于 {@link DatabaseMetaData#getColumns} 元数据 API，
     * 不会读取业务数据，也不要求 SQL 写权限。
     */
    private boolean hasColumn(Connection conn, String table, String column) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(
                conn.getCatalog(), null, table, column)) {
            return rs.next();
        }
    }
}
