package com.campusforum.security.xss;

import com.campusforum.infra.sanitize.HtmlSanitizerService;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Arbitraries;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 任务 TPBT.1 / design.md Property 1：HTML Sanitizer 输出永远安全 + 幂等。
 *
 * <p>本测试基于 jqwik 框架，针对 {@link HtmlSanitizerService} 的三个对外方法
 * （{@code sanitizePost} / {@code sanitizeComment} / {@code sanitizeMessage}）
 * 验证两条核心安全属性：</p>
 *
 * <ol>
 *   <li><b>安全性（Property A）</b>：对任意输入 {@code s}，{@code sanitize(s)} 输出
 *       不应包含已知 XSS 载荷标记（大小写不敏感）：
 *       <ul>
 *         <li>{@code <script}（脚本注入）</li>
 *         <li>{@code onerror=} / {@code onload=} / {@code onclick=}（事件处理属性）</li>
 *         <li>{@code javascript:}（伪协议）</li>
 *       </ul>
 *       OWASP HTML Sanitizer 在 COMMENT_POLICY 下应剥离上述全部 token。</li>
 *
 *   <li><b>幂等性（Property B）</b>：{@code sanitize(sanitize(s)) == sanitize(s)}。
 *       这避免净化结果不稳定的情况——如果第二次净化能改变结果，意味着 sanitizer
 *       本身的输出可能仍含可被进一步降级的语义载荷，对前端渲染存在二次风险。</li>
 * </ol>
 *
 * <p>本测试纯单元，不依赖 Spring 上下文 / DB / Redis，可直接 {@code mvn -Dtest=HtmlSanitizerPropertyTest test} 跑。</p>
 */
class HtmlSanitizerPropertyTest {

    private final HtmlSanitizerService sanitizer = new HtmlSanitizerService();

    /**
     * 自定义生成器：构造"含潜在 XSS 载荷的 HTML 片段"。
     *
     * <p>jqwik 默认 {@code @ForAll String} 生成的多是普通 ASCII 串，
     * 命中 sanitizer 净化逻辑的概率较低。这里显式拼接危险 token + 任意纯文本，
     * 既覆盖"恶意载荷"又覆盖"边界字符"，让属性断言在最少试验次数下尽可能触发净化路径。</p>
     */
    @Provide
    Arbitrary<String> dangerousHtmlFragments() {
        Arbitrary<String> tokens = Arbitraries.of(
                "<script>alert(1)</script>",
                "<img src=x onerror=alert(1)>",
                "<a href='javascript:alert(1)'>x</a>",
                "<svg onload=alert(1)>",
                "<iframe src='javascript:alert(1)'>",
                "<div onclick=alert(1)>",
                "<style>@import 'x'</style>",
                "<a href=\"data:text/html,<script>1</script>\">x</a>",
                "><script>",
                "正常中文段落",
                "纯文本",
                "<b>加粗</b>",
                "<p>段落 with mixed <em>tags</em></p>",
                "");
        // 把 1-3 段拼接，形成更复杂的输入
        return tokens.list().ofMinSize(1).ofMaxSize(3)
                .map(parts -> String.join(" ", parts));
    }

    @Property(tries = 200)
    void sanitizePost_neverContainsXssTokens(@ForAll("dangerousHtmlFragments") String input) {
        assertOutputSafe(sanitizer.sanitizePost(input));
    }

    @Property(tries = 200)
    void sanitizeComment_neverContainsXssTokens(@ForAll("dangerousHtmlFragments") String input) {
        assertOutputSafe(sanitizer.sanitizeComment(input));
    }

    @Property(tries = 200)
    void sanitizeMessage_neverContainsXssTokens(@ForAll("dangerousHtmlFragments") String input) {
        assertOutputSafe(sanitizer.sanitizeMessage(input));
    }

    @Property(tries = 200)
    void sanitizePost_isIdempotent(@ForAll("dangerousHtmlFragments") String input) {
        String once = sanitizer.sanitizePost(input);
        String twice = sanitizer.sanitizePost(once == null ? null : once);
        assertThat(twice).isEqualTo(once);
    }

    @Property(tries = 200)
    void sanitizeComment_isIdempotent(@ForAll("dangerousHtmlFragments") String input) {
        String once = sanitizer.sanitizeComment(input);
        String twice = sanitizer.sanitizeComment(once == null ? null : once);
        assertThat(twice).isEqualTo(once);
    }

    /**
     * 任意 ASCII / Unicode 字符串也不能让 sanitizer 输出危险 token。
     *
     * <p>这条属性是"模糊兜底"——即使 jqwik 生成的不是构造好的恶意 HTML，
     * 而是随机字节序列，sanitizer 也不能在某个角落字符组合下放过 XSS 标记。</p>
     */
    @Property(tries = 100)
    void sanitizeComment_anyString_isSafe(@ForAll String anyInput) {
        assertOutputSafe(sanitizer.sanitizeComment(anyInput));
    }

    /**
     * 安全性断言：净化输出不得含已知 XSS 标记（大小写不敏感）。
     */
    private void assertOutputSafe(String out) {
        if (out == null) return;
        String low = out.toLowerCase(Locale.ROOT);
        assertThat(low)
                .as("sanitized output 必须不含 XSS 标记，实际为：" + out)
                .doesNotContain("<script")
                .doesNotContain("onerror=")
                .doesNotContain("onload=")
                .doesNotContain("onclick=")
                .doesNotContain("javascript:");
    }
}
