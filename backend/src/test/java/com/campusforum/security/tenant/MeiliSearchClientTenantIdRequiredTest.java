package com.campusforum.security.tenant;

import com.campusforum.infra.metrics.SecurityMetrics;
import com.campusforum.search.service.MeiliSearchClient;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MeiliSearchClient.search tenantId 强制契约测试。
 *
 * <p>对应任务：T6.2（bugfix.md 漏洞 22：MeiliSearch tenantId 缺失静默放行
 * → 跨租户搜索泄漏）。</p>
 *
 * <p>关键安全属性：
 * <ul>
 *   <li>调用 {@link MeiliSearchClient#search(String, String, int, Long)}
 *       时若 {@code tenantId == null}，必须返回空列表（拒绝放行），
 *       且 <b>不</b>把搜索请求发给 MeiliSearch 后端；</li>
 *   <li>同时累加 {@code tenant_violation_total{reason="missing_tenant_in_search"}}
 *       Counter，便于运维识别"哪些代码路径仍漏过了租户上下文"；</li>
 *   <li>当 {@code search.type != "meilisearch"}（active = false）时，
 *       直接返回空列表且 <b>不</b> 触发 tenant_violation 埋点，因为
 *       这种情况下根本没有 MeiliSearch 后端可走，属于功能未启用而非安全违规；</li>
 *   <li>历史 3 参重载 {@code search(String, String, int)} 已被删除，
 *       保证调用方在编译期就必须显式传租户 ID。</li>
 * </ul>
 * </p>
 *
 * <p>本测试使用真实 {@link SimpleMeterRegistry} + 真实 {@link SecurityMetrics}
 * 而非 mock，便于在内存级断言 Counter 计数。MeiliSearch 后端不可达
 * （host 指向回环端口），因此即便 active=true 也不会真的发出 HTTP 请求
 * （null tenant 的早期短路路径会优先返回，永远走不到 RestTemplate.exchange）。</p>
 */
class MeiliSearchClientTenantIdRequiredTest {

    /**
     * <b>Validates: 漏洞 22</b> — tenantId 缺失时拒绝搜索 + 上报 metrics。
     */
    @Test
    @DisplayName("nullTenant_returnsEmpty_andCounts: tenantId=null 时返回空列表并累加违规 Counter")
    void nullTenant_returnsEmpty_andCounts() {
        // 用真实 MeterRegistry + 真实 SecurityMetrics，便于断言 Counter 计数
        MeterRegistry registry = new SimpleMeterRegistry();
        SecurityMetrics securityMetrics = new SecurityMetrics(registry);

        // active=true（type=meilisearch）但 host 指向回环冷端口，
        // 以确保万一短路逻辑失效也能立即失败而非真发 HTTP 请求
        MeiliSearchClient client = new MeiliSearchClient(
                "meilisearch",
                "http://127.0.0.1:1",
                "",
                securityMetrics);

        // 行为契约：tenantId=null 必须返回空列表（不等于 null，避免 NPE）
        List<Map<String, Object>> hits = client.search("posts", "kw", 10, null);
        assertThat(hits).isEmpty();

        // 行为契约：累加 tenant_violation_total{reason="missing_tenant_in_search"}
        Counter counter = registry.find("tenant_violation")
                .tag("reason", "missing_tenant_in_search")
                .counter();
        assertThat(counter)
                .as("应当累加 tenant_violation Counter 以便运维识别此类调用栈")
                .isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    /**
     * <b>Validates: 漏洞 22 保留性约束</b> —
     * 当 MeiliSearch 引擎未启用（active=false）时，缺租户不应被识别为"安全违规"，
     * 因为根本没有跨租户搜索的载体；保留现状直接返回空列表即可。
     */
    @Test
    @DisplayName("inactiveSearch_returnsEmpty_evenWithTenant: 引擎关闭时一律返回空且不触发 metrics")
    void inactiveSearch_returnsEmpty_evenWithTenant() {
        MeterRegistry registry = new SimpleMeterRegistry();
        SecurityMetrics securityMetrics = new SecurityMetrics(registry);

        // type=mysql → active=false，整个搜索路径在第一行就短路返回
        MeiliSearchClient client = new MeiliSearchClient(
                "mysql",
                "http://127.0.0.1:1",
                "",
                securityMetrics);

        // 即便传入合法 tenantId 也不会调用任何 HTTP 接口，仅返回空
        assertThat(client.search("posts", "kw", 10, 42L)).isEmpty();
        // 缺租户场景不应被误判为安全违规：active=false 路径不上报
        assertThat(client.search("posts", "kw", 10, null)).isEmpty();

        // 无任何 tenant_violation 计数
        Counter counter = registry.find("tenant_violation")
                .tag("reason", "missing_tenant_in_search")
                .counter();
        assertThat(counter)
                .as("引擎未启用时不应触发跨租户违规埋点")
                .isNull();
    }

    /**
     * <b>Validates: 漏洞 22 设计要求</b> —
     * 历史 3 参重载 {@code search(String, String, int)} 已被删除，
     * 保证编译期就阻止"忘记传租户"的调用方。本测试用反射在运行时
     * 验证：方法不存在即视为契约满足。
     */
    @Test
    @DisplayName("removed_3argSearch_method_doesNotExist: 旧 3 参 search 重载已删除")
    void removed_3argSearch_method_doesNotExist() {
        // 反射检索 3 参 search(String, String, int)：必须找不到
        assertThatThrownBy(() -> MeiliSearchClient.class.getMethod(
                "search", String.class, String.class, int.class))
                .isInstanceOf(NoSuchMethodException.class);

        // 同时确认 4 参 search(String, String, int, Long) 仍然存在，
        // 防止改动把整组方法都误删
        try {
            var method = MeiliSearchClient.class.getMethod(
                    "search", String.class, String.class, int.class, Long.class);
            assertThat(method).isNotNull();
        } catch (NoSuchMethodException e) {
            throw new AssertionError("4 参 search(String, String, int, Long) 必须保留", e);
        }
    }
}
