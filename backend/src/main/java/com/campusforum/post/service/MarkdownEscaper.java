package com.campusforum.post.service;

/**
 * Markdown 控制字符转义工具。
 *
 * <p>对应 bugfix.md 漏洞 20：{@code PostService.create} 在拼接引用块时把
 * nickname / title / content 直接拼进 markdown 字符串，恶意昵称如
 * {@code "**X**\n# H1\n>"} 可让引用块 "破出" 边界，让攻击者控制后续渲染
 * （例如插入伪造的标题、列表、代码块）。</p>
 *
 * <p>本工具把 markdown 控制字符转义为 {@code \\xxx}，确保拼接后渲染器只把
 * 它们当作字面字符处理。</p>
 *
 * <p><b>边界</b>：本工具仅做 markdown 语法字符转义，不做 HTML 净化
 * （HTML 净化由 {@code HtmlSanitizerService} 在最终落库前完成）。</p>
 */
public final class MarkdownEscaper {

    private MarkdownEscaper() {}

    /**
     * 转义 markdown 控制字符。
     *
     * <p>覆盖：{@code \ * _ ` [ ] ( ) # + - ! >}。这些字符是 CommonMark / GFM
     * 规范中触发语法行为的最小集合：粗斜体（{@code * _}）、行内代码（{@code `}）、
     * 链接 / 图片（{@code [ ] ( ) !}）、标题（{@code #}）、列表（{@code + -}）、
     * 引用块（{@code >}）。</p>
     *
     * @param s 原始字符串
     * @return 转义后的字符串；{@code null} 透传为空串
     */
    public static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("`", "\\`")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("#", "\\#")
                .replace("+", "\\+")
                .replace("-", "\\-")
                .replace("!", "\\!")
                .replace(">", "\\>");
    }
}
