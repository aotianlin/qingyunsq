package com.campusforum.security.ratelimit;

import com.campusforum.infra.ratelimit.RouteTemplateExtractor;
import com.campusforum.infra.ratelimit.RouteTemplateExtractor.ExtractResult;
import jakarta.servlet.http.HttpServletRequest;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.HandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 任务 TPBT.3（部分） / design.md Property 3：限流路由模板共享桶。
 *
 * <p>原任务设计需要 Testcontainers Redis 模拟"并发请求受 maxRequests 约束"，
 * 仓库约定禁用 Testcontainers，因此本测试退化到验证
 * {@link RouteTemplateExtractor} 的核心不变量——这是"共享桶"语义的基础：</p>
 *
 * <ol>
 *   <li><b>属性 A（核心）</b>：N 个不同的 path variable ID 路径，只要它们落到同一个
 *       Spring MVC 路由模板（如 {@code /api/v1/posts/{id}}），
 *       {@code extract().key} 必须相同，从而在上层 {@code RateLimitInterceptor}
 *       内汇聚到同一个限流桶；</li>
 *   <li><b>属性 B（兜底）</b>：当 Spring MVC 没有写入
 *       {@link HandlerMapping#BEST_MATCHING_PATTERN_ATTRIBUTE} 时，
 *       {@code extract().isTemplate} 必须为 {@code false}，调用方据此走"配额减半"兜底。</li>
 * </ol>
 *
 * <p>本测试纯单元，不依赖 Redis / SpringBootTest。属性测试覆盖 100 个随机 ID。</p>
 */
class RouteTemplateBucketPropertyTest {

    private final RouteTemplateExtractor extractor = new RouteTemplateExtractor();

    @Provide
    Arbitrary<String> templates() {
        return Arbitraries.of(
                "/api/v1/posts/{id}",
                "/api/v1/posts/{id}/comments",
                "/api/v1/resources/{id}/download",
                "/api/v1/resources/{id}/preview",
                "/api/v1/users/{id}/profile"
        );
    }

    @Property(tries = 100)
    void differentIds_sameTemplate_shareSameKey(
            @ForAll("templates") String template,
            @ForAll @LongRange(min = 1, max = 1_000_000_000L) long id1,
            @ForAll @LongRange(min = 1, max = 1_000_000_000L) long id2) {
        // 模拟两次请求落到同一个模板，只是 path variable 不同
        HttpServletRequest req1 = mockRequestWithTemplate(template,
                template.replace("{id}", String.valueOf(id1)));
        HttpServletRequest req2 = mockRequestWithTemplate(template,
                template.replace("{id}", String.valueOf(id2)));

        ExtractResult r1 = extractor.extract(req1);
        ExtractResult r2 = extractor.extract(req2);

        assertThat(r1.isTemplate()).isTrue();
        assertThat(r2.isTemplate()).isTrue();
        // 关键属性：key 必须一致 → 同一限流桶
        assertThat(r1.key())
                .as("不同 path variable ID 落到同一模板 [%s] 时，限流 key 必须一致以共享桶",
                        template)
                .isEqualTo(r2.key())
                .isEqualTo(template);
    }

    @Property(tries = 50)
    void missingAttribute_fallsBackToRawUriAndMarksNonTemplate(
            @ForAll @LongRange(min = 1, max = 1_000_000L) long id) {
        // 模拟未匹配到路由的请求（attribute 为 null）
        String rawUri = "/some/path/" + id;
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)).thenReturn(null);
        when(req.getRequestURI()).thenReturn(rawUri);

        ExtractResult r = extractor.extract(req);
        assertThat(r.isTemplate()).isFalse();
        assertThat(r.key()).isEqualTo(rawUri);
    }

    @Test
    void blankAttribute_alsoFallsBack() {
        // 边界：attribute 是空串 / 空白也走兜底
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)).thenReturn("   ");
        when(req.getRequestURI()).thenReturn("/raw");
        ExtractResult r = extractor.extract(req);
        assertThat(r.isTemplate()).isFalse();
        assertThat(r.key()).isEqualTo("/raw");
    }

    @Test
    void nullUri_returnsEmptyKey() {
        // 边界：raw URI 也是 null（非常规情况）
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)).thenReturn(null);
        when(req.getRequestURI()).thenReturn(null);
        ExtractResult r = extractor.extract(req);
        assertThat(r.isTemplate()).isFalse();
        assertThat(r.key()).isEmpty();
    }

    private HttpServletRequest mockRequestWithTemplate(String template, String rawUri) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE))
                .thenReturn(template);
        when(req.getRequestURI()).thenReturn(rawUri);
        return req;
    }
}
