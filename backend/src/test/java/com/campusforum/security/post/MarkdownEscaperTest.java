package com.campusforum.security.post;

import com.campusforum.post.service.MarkdownEscaper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MarkdownEscaper} 单元测试。
 *
 * <p>对应 bugfix.md 漏洞 20：验证 markdown 控制字符被正确转义，
 * 让恶意 nickname / title / content 拼进引用块时不会破坏 markdown 边界。</p>
 */
class MarkdownEscaperTest {

    @Test
    @DisplayName("escapes_all_controlChars：粗体语法被转义")
    void escapes_all_controlChars() {
        String input = "**bold**";
        String output = MarkdownEscaper.escape(input);

        // 输出必须不含未转义的 *
        assertThat(output).doesNotContain("**");
        // 但应包含转义后的形式
        assertThat(output).contains("\\*");
        assertThat(output).isEqualTo("\\*\\*bold\\*\\*");
    }

    @Test
    @DisplayName("escapes_blockQuote：> 被转义为 \\>")
    void escapes_blockQuote() {
        String input = "> quote";
        String output = MarkdownEscaper.escape(input);

        assertThat(output).contains("\\>");
        assertThat(output).isEqualTo("\\> quote");
    }

    @Test
    @DisplayName("escapes_link：链接语法 [x](y) 被转义")
    void escapes_link() {
        String input = "[x](y)";
        String output = MarkdownEscaper.escape(input);

        assertThat(output).isEqualTo("\\[x\\]\\(y\\)");
    }

    @Test
    @DisplayName("null_returnsEmpty：null 输入返回空串")
    void null_returnsEmpty() {
        assertThat(MarkdownEscaper.escape(null)).isEqualTo("");
    }

    @Test
    @DisplayName("nonControlChars_preserved：普通文字 / 中文 / 数字保持原样")
    void nonControlChars_preserved() {
        // 普通中英文 + 数字 + 空格不应被转义
        String input = "普通文字 hello 123";
        assertThat(MarkdownEscaper.escape(input)).isEqualTo(input);
    }

    @Test
    @DisplayName("escapes_backslash_andHash：反斜杠 / 标题 / 列表 / 图片标记被转义")
    void escapes_misc_controlChars() {
        // 反斜杠：先转义为 \\，避免后续转义产生歧义
        assertThat(MarkdownEscaper.escape("\\")).isEqualTo("\\\\");
        // # 标题
        assertThat(MarkdownEscaper.escape("# title")).isEqualTo("\\# title");
        // - 列表
        assertThat(MarkdownEscaper.escape("- item")).isEqualTo("\\- item");
        // + 列表
        assertThat(MarkdownEscaper.escape("+ item")).isEqualTo("\\+ item");
        // ! 图片
        assertThat(MarkdownEscaper.escape("![alt](url)")).isEqualTo("\\!\\[alt\\]\\(url\\)");
        // ` 行内代码
        assertThat(MarkdownEscaper.escape("`code`")).isEqualTo("\\`code\\`");
        // _ 下划线
        assertThat(MarkdownEscaper.escape("_emph_")).isEqualTo("\\_emph\\_");
    }
}
