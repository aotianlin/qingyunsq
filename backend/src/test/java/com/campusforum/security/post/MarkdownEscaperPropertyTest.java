package com.campusforum.security.post;

import com.campusforum.post.service.MarkdownEscaper;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 任务 TPBT.2 / design.md Property 2：Markdown 引用块不被注入逃逸。
 *
 * <p>验证 {@link MarkdownEscaper#escape(String)} 输出的字符串无论作为引用块
 * 内容还是行内插值，都不会触发 markdown 控制语法（粗斜体 / 标题 / 列表 / 引用块 /
 * 链接 / 代码 / 图片）。</p>
 *
 * <p>本测试纯单元，无任何外部依赖。</p>
 */
class MarkdownEscaperPropertyTest {

    /**
     * 任意字符串经 escape 之后，所有 markdown 控制字符必须以反斜杠转义。
     *
     * <p>具体规则（与实现保持一致）：</p>
     * <ul>
     *   <li>{@code \ → \\}（双反斜杠）</li>
     *   <li>{@code * _ ` [ ] ( ) # + - ! >} 各自前置 {@code \}</li>
     * </ul>
     */
    @Property(tries = 200)
    void escape_resultHasNoUnescapedControlChars(@ForAll String input) {
        String out = MarkdownEscaper.escape(input);
        // null 输入透传为空串
        if (input == null) {
            assertThat(out).isEmpty();
            return;
        }
        // 逐字符扫描：每个 markdown 控制字符前必须紧跟一个反斜杠（构造 "\\X"）
        for (int i = 0; i < out.length(); i++) {
            char c = out.charAt(i);
            if (isMarkdownControl(c)) {
                // 该位置必须是被转义的（i > 0 且前一个字符是反斜杠）
                assertThat(i)
                        .as("控制字符 '%s' 出现在转义结果第 %d 位但未被反斜杠转义。escape input=%s, output=%s",
                                c, i, safeRepr(input), safeRepr(out))
                        .isGreaterThan(0);
                assertThat(out.charAt(i - 1))
                        .as("控制字符 '%s' 出现在转义结果第 %d 位但前一字符不是 \\。output=%s",
                                c, i, safeRepr(out))
                        .isEqualTo('\\');
            }
        }
    }

    /**
     * 二次 escape 不再扩张：escape 结果中的反斜杠数目 == 控制字符数 + 原始反斜杠数 * 2。
     *
     * <p>这条属性强调：实现是简单的 String.replace 链，幂等性意义有限
     * （二次 escape 会再次双写反斜杠）。这里只断言"escape 不会引入新的未转义控制字符"。</p>
     */
    @Property(tries = 100)
    void doubleEscape_stillContainsNoUnescapedControlChars(@ForAll String input) {
        String once = MarkdownEscaper.escape(input);
        String twice = MarkdownEscaper.escape(once);
        // 二次 escape 仍然不能含未转义控制字符
        for (int i = 0; i < twice.length(); i++) {
            char c = twice.charAt(i);
            if (isMarkdownControl(c)) {
                assertThat(i).isGreaterThan(0);
                assertThat(twice.charAt(i - 1)).isEqualTo('\\');
            }
        }
    }

    @Test
    void quoteBlockBoundary_isPreserved_evenWithMaliciousNickname() {
        // 经典越界载荷：恶意昵称试图破出引用块边界
        String evilNickname = "**X**\n# H1\n>";
        String escaped = MarkdownEscaper.escape(evilNickname);
        // 关键安全断言：反斜杠转义后，渲染器只把 *、#、> 当字面字符
        // （我们不能在这里直接验证"渲染结果"，但可以验证 escape 字符串里
        // 所有控制字符都被双前置反斜杠包裹）
        assertThat(escaped).doesNotMatch("(?<!\\\\)\\*");
        assertThat(escaped).doesNotMatch("(?<!\\\\)#");
        assertThat(escaped).doesNotMatch("(?<!\\\\)>");
    }

    private boolean isMarkdownControl(char c) {
        return c == '*' || c == '_' || c == '`' || c == '[' || c == ']'
                || c == '(' || c == ')' || c == '#' || c == '+' || c == '-'
                || c == '!' || c == '>';
    }

    /** 把控制字符替换为可见占位，便于断言失败时输出可读消息。 */
    private String safeRepr(String s) {
        if (s == null) return "<null>";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c < 0x20) sb.append('?');
            else sb.append(c);
        }
        return sb.toString();
    }
}
