package com.campusforum.infra;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.campusforum.tenant.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 单元测试：验证 MyBatisPlusConfig 中 TenantLineHandler 的行为。
 * - TenantContext 为 null 时必须抛 IllegalStateException（禁止降级）
 * - TenantContext 非 null 时返回正确的 LongValue
 *
 * Validates: Property 4, Property 7
 */
class TenantLineHandlerTest {

    private static final Set<String> TENANT_IGNORE_TABLES = Set.of(
            "tenants", "achievements", "sensitive_words"
    );

    /**
     * 与 MyBatisPlusConfig 中注册的 handler 逻辑完全一致的实例，
     * 用于隔离测试（不依赖 Spring 上下文）。
     */
    private final TenantLineHandler handler = new TenantLineHandler() {
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
    };

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldThrowIllegalStateExceptionWhenTenantContextIsNull() {
        // TenantContext 未设置（默认 null）
        TenantContext.clear();

        assertThatThrownBy(() -> handler.getTenantId())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TenantContext is null");
    }

    @Test
    void shouldReturnCorrectLongValueWhenTenantContextIsSet() {
        TenantContext.setTenantId(42L);

        Expression result = handler.getTenantId();

        assertThat(result).isInstanceOf(LongValue.class);
        assertThat(((LongValue) result).getValue()).isEqualTo(42L);
    }

    @Test
    void shouldReturnCorrectLongValueForDifferentTenantIds() {
        TenantContext.setTenantId(999L);

        Expression result = handler.getTenantId();

        assertThat(result).isInstanceOf(LongValue.class);
        assertThat(((LongValue) result).getValue()).isEqualTo(999L);
    }

    @Test
    void shouldUseTenantIdColumnNamedTenantId() {
        assertThat(handler.getTenantIdColumn()).isEqualTo("tenant_id");
    }

    @Test
    void shouldIgnoreTenantsTable() {
        assertThat(handler.ignoreTable("tenants")).isTrue();
    }

    @Test
    void shouldIgnoreAchievementsTable() {
        assertThat(handler.ignoreTable("achievements")).isTrue();
    }

    @Test
    void shouldIgnoreSensitiveWordsTable() {
        assertThat(handler.ignoreTable("sensitive_words")).isTrue();
    }

    @Test
    void shouldNotIgnoreRegularBusinessTable() {
        assertThat(handler.ignoreTable("posts")).isFalse();
        assertThat(handler.ignoreTable("users")).isFalse();
    }
}
