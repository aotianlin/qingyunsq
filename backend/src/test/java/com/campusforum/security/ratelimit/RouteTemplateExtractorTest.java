package com.campusforum.security.ratelimit;

import com.campusforum.infra.ratelimit.RouteTemplateExtractor;
import com.campusforum.infra.ratelimit.RouteTemplateExtractor.ExtractResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RouteTemplateExtractor} 单元测试。
 *
 * <p>验证 T5.1：在 Spring MVC 写入 {@link HandlerMapping#BEST_MATCHING_PATTERN_ATTRIBUTE}
 * 的常规路径下取到路由模板（{@code isTemplate=true}），在 attribute 缺失或异常情况下
 * 退回到原始 URI 并把 {@code isTemplate} 置为 {@code false}。</p>
 *
 * <p>使用 {@link MockHttpServletRequest} 而非 Mockito，避免引入额外依赖，
 * 同时这是 Spring 团队官方提供的 servlet mock，与拦截器实际运行环境保持一致。</p>
 */
class RouteTemplateExtractorTest {

    private final RouteTemplateExtractor extractor = new RouteTemplateExtractor();

    @Test
    @DisplayName("setAttribute(BEST_MATCHING_PATTERN, 模板) 时返回模板且 isTemplate=true")
    void returnsBestMatchingPattern() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/posts/42");
        req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/posts/{id}");

        ExtractResult result = extractor.extract(req);

        assertThat(result.key()).isEqualTo("/api/v1/posts/{id}");
        assertThat(result.isTemplate()).isTrue();
    }

    @Test
    @DisplayName("attribute 缺失时退回原始 URI 且 isTemplate=false")
    void returnsRawUri_whenAttributeMissing() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/foo/bar");
        // 不调用 setAttribute，模拟 404 / 静态资源等场景

        ExtractResult result = extractor.extract(req);

        assertThat(result.key()).isEqualTo("/api/v1/foo/bar");
        assertThat(result.isTemplate()).isFalse();
    }

    @Test
    @DisplayName("attribute 为空白字符串时退回原始 URI 且 isTemplate=false")
    void returnsRawUri_whenAttribute_blank() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/baz");
        // 模拟极端情况：attribute 被设置为空白字符串，仍应视作未命中模板
        req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "   ");

        ExtractResult result = extractor.extract(req);

        assertThat(result.key()).isEqualTo("/api/v1/baz");
        assertThat(result.isTemplate()).isFalse();
    }

    @Test
    @DisplayName("attribute 缺失且 URI 为 null 时返回空字符串且 isTemplate=false")
    void returnsEmptyString_whenAttributeAndUriBothNull() {
        // MockHttpServletRequest 的 getRequestURI 默认返回 ""，因此用匿名子类强制返回 null
        // 以验证 RouteTemplateExtractor 的最终兜底分支
        MockHttpServletRequest req = new MockHttpServletRequest() {
            @Override
            public String getRequestURI() {
                return null;
            }
        };

        ExtractResult result = extractor.extract(req);

        assertThat(result.key()).isEqualTo("");
        assertThat(result.isTemplate()).isFalse();
    }
}
