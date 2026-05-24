package com.campusforum.infra.sanitize;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link HtmlSanitizerService#decodeSafeEntities(String)} 的安全边界测试。
 *
 * <p>核心安全约束：仅解码 codepoint &gt;= 0x80 的非 ASCII 数字字符引用，
 * 绝不还原 ASCII 范围（特别是 {@code &lt;} {@code &gt;} {@code &amp;} {@code &quot;}
 * 等）。否则 OWASP Sanitizer 的输出经我方二次"美化"后会把已经被 sanitizer
 * 转义掉的 XSS 载荷"复活"——这是经典的"双向编码"缺陷。</p>
 */
class HtmlSanitizerEntityDecodeTest {

    @Test
    void nonAsciiNumericRef_isDecoded() {
        // 中文全角逗号 = U+FF0C
        assertThat(HtmlSanitizerService.decodeSafeEntities("内容&#xff0c;后续"))
                .isEqualTo("内容，后续");
        // 全角冒号 = U+FF1A
        assertThat(HtmlSanitizerService.decodeSafeEntities("提示&#xff1a;注意"))
                .isEqualTo("提示：注意");
        // 十进制 NCR：CJK 等价于 &#65292;
        assertThat(HtmlSanitizerService.decodeSafeEntities("a&#65292;b"))
                .isEqualTo("a，b");
    }

    @Test
    void asciiNumericRef_isPreserved() {
        // &lt; / &gt; 的十六进制 NCR：&#x3c; / &#x3e;
        assertThat(HtmlSanitizerService.decodeSafeEntities("&#x3c;script&#x3e;"))
                .as("ASCII 范围 NCR 不能还原，否则 < > 会被复活")
                .isEqualTo("&#x3c;script&#x3e;");
        // &lt; 的十进制 NCR：&#60;
        assertThat(HtmlSanitizerService.decodeSafeEntities("&#60;img&#62;"))
                .isEqualTo("&#60;img&#62;");
    }

    @Test
    void namedEntities_arePreserved() {
        // &lt; &gt; &amp; &quot; &apos; 命名实体保持原样
        assertThat(HtmlSanitizerService.decodeSafeEntities("&lt;script&gt;alert(1)&lt;/script&gt;"))
                .isEqualTo("&lt;script&gt;alert(1)&lt;/script&gt;");
        assertThat(HtmlSanitizerService.decodeSafeEntities("&amp; &quot; &apos;"))
                .isEqualTo("&amp; &quot; &apos;");
    }

    @Test
    void mixedInput_decodesNonAsciiOnly() {
        // 混合：非 ASCII NCR + ASCII NCR + 命名实体 + 普通字符
        String input = "你&amp;我&#xff0c;&#x3c;tag&#x3e;&#x4e2d;文";
        // &#xff0c; → ，；&#x4e2d; → 中；其余保持
        String expected = "你&amp;我，&#x3c;tag&#x3e;中文";
        assertThat(HtmlSanitizerService.decodeSafeEntities(input)).isEqualTo(expected);
    }

    @Test
    void malformedReference_isPreserved() {
        // 格式错的不应崩溃，原样返回
        assertThat(HtmlSanitizerService.decodeSafeEntities("&#xZZZZ;"))
                .isEqualTo("&#xZZZZ;");
        assertThat(HtmlSanitizerService.decodeSafeEntities("&#;"))
                .isEqualTo("&#;");
        assertThat(HtmlSanitizerService.decodeSafeEntities("&#"))
                .isEqualTo("&#");
        assertThat(HtmlSanitizerService.decodeSafeEntities(""))
                .isEqualTo("");
        assertThat(HtmlSanitizerService.decodeSafeEntities(null))
                .isNull();
    }

    @Test
    void emojiCodepoint_isDecoded() {
        // emoji 也是高码位，应该正常还原（U+1F600 笑脸）
        assertThat(HtmlSanitizerService.decodeSafeEntities("hi &#x1f600; there"))
                .isEqualTo("hi 😀 there");
    }

    @Test
    void endToEnd_sanitizeKeepsChinesePunctuationReadable() {
        HtmlSanitizerService svc = new HtmlSanitizerService();
        String input = "这是测试帖子的内容，用于验证发帖功能。";
        // 经过净化后中文标点必须可读（不能是 NCR 形式）
        assertThat(svc.sanitizePost(input)).isEqualTo(input);
        assertThat(svc.sanitizeComment(input)).isEqualTo(input);
        assertThat(svc.sanitizeMessage(input)).isEqualTo(input);
    }

    @Test
    void endToEnd_sanitizeStillRemovesXssPayload() {
        HtmlSanitizerService svc = new HtmlSanitizerService();
        // 关键回归：解码安全实体后 XSS 载荷依然被剥离
        String evil = "<script>alert(1)</script>正常内容，结尾";
        String out = svc.sanitizeComment(evil);
        assertThat(out).doesNotContain("<script");
        assertThat(out).doesNotContain("alert(1)");
        assertThat(out).contains("正常内容，结尾");
    }
}
