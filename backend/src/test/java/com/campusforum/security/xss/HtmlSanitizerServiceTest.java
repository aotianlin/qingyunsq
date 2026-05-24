package com.campusforum.security.xss;

import com.campusforum.infra.sanitize.HtmlSanitizerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link HtmlSanitizerService} 单元测试。
 *
 * <p>对应 bugfix.md 漏洞 18 与 design.md 主题 8 — 验证 OWASP HTML Sanitizer 在
 * 典型 XSS 载荷下的过滤效果，以及"评论 / 私信策略不允许图片"等差异化策略。</p>
 *
 * <p>本测试不依赖 Spring 上下文 / MySQL / Redis，可在纯 JVM 下直接运行。</p>
 */
class HtmlSanitizerServiceTest {

    /** 待测对象。Service 无状态，每个用例独立 new 即可。 */
    private final HtmlSanitizerService service = new HtmlSanitizerService();

    @Test
    @DisplayName("sanitizePost：剥离 <script> 标签")
    void removes_script_tag() {
        String input = "<script>alert(1)</script>hello";
        String output = service.sanitizePost(input);

        // <script> 必须被完全剥离；正文内容保留
        assertThat(output).doesNotContain("<script");
        assertThat(output).doesNotContain("alert(1)");
        assertThat(output).contains("hello");
    }

    @Test
    @DisplayName("sanitizePost：剥离 onerror 等事件处理属性")
    void removes_onerror_attribute() {
        String input = "<img src=\"x\" onerror=\"alert(1)\">";
        String output = service.sanitizePost(input);

        // onerror 属性与其内嵌 JS 必须被剥离
        assertThat(output).doesNotContainIgnoringCase("onerror");
        assertThat(output).doesNotContain("alert(1)");
    }

    @Test
    @DisplayName("sanitizePost：javascript: 协议 URL 被剥离或拒绝")
    void removes_javascript_url() {
        String input = "<a href=\"javascript:alert(1)\">x</a>";
        String output = service.sanitizePost(input);

        // 不允许 javascript: 协议存在；脚本字符串也必须不出现
        assertThat(output).doesNotContainIgnoringCase("javascript:");
        assertThat(output).doesNotContain("alert(1)");
    }

    @Test
    @DisplayName("sanitizePost：保留安全的 Markdown 渲染标签（粗体 / 链接）")
    void keeps_safe_markdown() {
        String input = "<p><strong>bold</strong> <a href=\"https://example.com\">link</a></p>";
        String output = service.sanitizePost(input);

        // 粗体与外链应被保留；href 指向 https 协议的合法 URL
        assertThat(output).contains("<strong>bold</strong>");
        assertThat(output).contains("link");
        assertThat(output).contains("https://example.com");
    }

    @Test
    @DisplayName("sanitizeComment：评论策略不允许 <img> 标签")
    void comment_strips_image() {
        String input = "<p>hi <img src=\"x.png\"/></p>";
        String output = service.sanitizeComment(input);

        // 评论 / 私信策略不包含 IMAGES，因此 <img> 必须被剥离；正文文本保留
        assertThat(output).doesNotContain("<img");
        assertThat(output).contains("hi");
    }

    @Test
    @DisplayName("sanitizePost：幂等性 — sanitize(sanitize(x)) == sanitize(x)")
    void is_idempotent() {
        String input = "<p><strong>bold</strong> "
                + "<a href=\"javascript:alert(1)\">x</a>"
                + "<script>alert(2)</script>"
                + "<img src=\"y\" onerror=\"alert(3)\"></p>";

        String once = service.sanitizePost(input);
        String twice = service.sanitizePost(once);

        // 二次净化结果必须与一次净化结果完全一致，避免业务"反复保存"造成内容退化
        assertThat(twice).isEqualTo(once);
    }

    @Test
    @DisplayName("sanitizePost / sanitizeComment / sanitizeMessage：null 输入返回 null")
    void null_returns_null() {
        // 业务在"内容可选"场景需要 null 透传，避免 NPE
        assertThat(service.sanitizePost(null)).isNull();
        assertThat(service.sanitizeComment(null)).isNull();
        assertThat(service.sanitizeMessage(null)).isNull();
    }
}
