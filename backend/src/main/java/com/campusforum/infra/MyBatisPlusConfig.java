package com.campusforum.infra;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.campusforum.tenant.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
public class MyBatisPlusConfig {

    /** 不需要做租户隔离的系统表/字典表 */
    private static final Set<String> TENANT_IGNORE_TABLES = Set.of(
            "tenants", "achievements", "sensitive_words"
    );

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 多租户 SQL 自动改写：始终注册，由 TenantContext.getTenantId() 决定运行期 tenant_id。
        // - standalone 模式：所有请求 TenantContext = standaloneTenantId，写入 SQL 的 tenant_id = 该值
        // - multi 模式：TenantContext 由 TenantResolutionFilter 根据 Sa-Token Session/子域名/X-Tenant-Id 解析得到
        // - TenantContext 为 null 时：直接抛 IllegalStateException，禁止静默降级
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                Long tenantId = TenantContext.getTenantId();
                if (tenantId == null) {
                    throw new IllegalStateException(
                        "TenantContext is null. This indicates a missing TenantResolutionFilter "
                        + "or an entry path that bypassed it (e.g., scheduled task without explicit TenantContext setup).");
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
        }));

        // 分页插件
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(100L);
        interceptor.addInnerInterceptor(pagination);

        return interceptor;
    }
}
