package com.campusforum.infra.sanitize;

import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.stereotype.Service;

/**
 * HTML 净化服务（OWASP Java HTML Sanitizer 包装）。
 *
 * <p>对应 bugfix.md 漏洞 18：pom 已引入 OWASP HTML Sanitizer 但全代码无调用方，
 * 用户提交的帖子 / 评论 / 私信内容直接落库再渲染，存在存储型 XSS 风险。
 * 本服务提供按场景预设的策略，所有用户内容写库前必须经过对应方法处理。</p>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>{@link #sanitizePost(String)}：帖子正文 — 保留 Markdown 渲染后的常见标签
 *       （格式化 / 链接 / 块级 / 图片 / 表格），移除 {@code <script>} / 事件处理属性
 *       （onerror / onclick 等）/ {@code javascript:} 协议 URL；</li>
 *   <li>{@link #sanitizeComment(String)}：评论正文 — 仅保留格式化与链接，
 *       不允许图片 / 表格 / 代码块，避免评论区被滥用为 XSS 载体；</li>
 *   <li>{@link #sanitizeMessage(String)}：私信正文 — 与评论同策略，
 *       因为私信通常更短、风险窗口更窄。</li>
 * </ul>
 *
 * <h2>幂等性</h2>
 * <p>OWASP Sanitizer 的所有 {@link PolicyFactory} 都是幂等的：
 * {@code sanitize(sanitize(x)).equals(sanitize(x))}。该性质对于"二次保存 / 重新编辑"
 * 等场景非常重要，业务无需担心多次净化导致内容退化。</p>
 *
 * <h2>线程安全</h2>
 * <p>{@link PolicyFactory} 实例本身线程安全，本服务为无状态 Spring 单例 Bean，
 * 可直接被任意 Service 注入并并发调用。</p>
 */
@Service
public class HtmlSanitizerService {

    /**
     * 帖子正文策略：格式化 + 链接 + 块级 + 图片 + 表格。
     * <p>覆盖 Markdown 渲染后的常见富文本标签，例如
     * {@code <p>}、{@code <strong>}、{@code <em>}、{@code <a>}、
     * {@code <ul>}/{@code <li>}、{@code <blockquote>}、{@code <img>}、{@code <table>} 等。</p>
     */
    private static final PolicyFactory POST_POLICY = Sanitizers.FORMATTING
            .and(Sanitizers.LINKS)
            .and(Sanitizers.BLOCKS)
            .and(Sanitizers.IMAGES)
            .and(Sanitizers.TABLES);

    /**
     * 评论 / 私信策略：仅格式化 + 链接。
     * <p>不允许图片 / 表格 / 代码块，控制评论区与私信的攻击面。</p>
     */
    private static final PolicyFactory COMMENT_POLICY = Sanitizers.FORMATTING
            .and(Sanitizers.LINKS);

    /**
     * 帖子正文净化：保留 Markdown 渲染后允许的标签，移除 {@code <script>} /
     * 事件处理属性 / {@code javascript:} 协议 URL。
     *
     * @param html 原始 HTML 内容（通常是 Markdown 渲染产物或前端富文本编辑器输出）
     * @return 净化后的 HTML；入参为 {@code null} 时直接返回 {@code null}，
     *         便于业务在"内容可选"场景透传。
     */
    public String sanitizePost(String html) {
        if (html == null) {
            return null;
        }
        return decodeSafeEntities(POST_POLICY.sanitize(html));
    }

    /**
     * 评论正文净化：仅保留格式化 + 链接。
     *
     * @param html 原始 HTML 内容
     * @return 净化后的 HTML；入参为 {@code null} 时直接返回 {@code null}
     */
    public String sanitizeComment(String html) {
        if (html == null) {
            return null;
        }
        return decodeSafeEntities(COMMENT_POLICY.sanitize(html));
    }

    /**
     * 私信正文净化：与评论同策略（格式化 + 链接）。
     *
     * @param html 原始 HTML 内容
     * @return 净化后的 HTML；入参为 {@code null} 时直接返回 {@code null}
     */
    public String sanitizeMessage(String html) {
        return sanitizeComment(html);
    }

    /**
     * 把 OWASP Sanitizer 输出中的"安全的非 ASCII 数字字符引用"还原为字面字符。
     *
     * <p><b>背景</b>：OWASP HTML Sanitizer 的默认编码策略相对保守，会把所有非 ASCII
     * 高码位字符（包括中文全角标点 {@code ，} {@code ：} {@code 。} 等）编码为
     * {@code &#xNNNN;} 形式的数字字符引用。这种编码在功能上完全等价于原字符
     * （浏览器渲染相同），但会让数据库里存储的字符串在前端非 HTML 渲染场景
     * （比如纯文本预览、移动端 IM 弹窗、邮件通知）显示成 {@code 内容&#xff0c;后续}
     * 这种乱码，严重影响用户体验。</p>
     *
     * <p><b>安全性约束</b>：本方法<b>仅</b>解码"非 ASCII 范围"（codepoint &gt;= 0x80）的
     * 数字字符引用，绝不还原 {@code &lt;} {@code &gt;} {@code &amp;} {@code &quot;}
     * {@code &apos;} 等命名实体或 ASCII 范围的 NCR——那些字符在 HTML 中有特殊语义，
     * 还原后会让 Sanitizer 之前剥离的 XSS 载荷"复活"。</p>
     *
     * <p>例子：</p>
     * <ul>
     *   <li>{@code 内容&#xff0c;后续} → {@code 内容，后续}（中文全角逗号，安全还原）</li>
     *   <li>{@code &lt;script&gt;} → {@code &lt;script&gt;}（保持转义，<b>不</b>还原）</li>
     *   <li>{@code &#x3c;script&#x3e;} → {@code &#x3c;script&#x3e;}（&lt; 和 &gt; 的 ASCII NCR，<b>不</b>还原）</li>
     *   <li>{@code &amp;} → {@code &amp;}（命名实体保持原样）</li>
     * </ul>
     *
     * @param sanitized OWASP Sanitizer 的输出
     * @return 仅解码非 ASCII NCR 后的 HTML，{@code null} 透传
     */
    static String decodeSafeEntities(String sanitized) {
        if (sanitized == null || sanitized.isEmpty()) {
            return sanitized;
        }
        // 仅匹配 codepoint >= 0x80 的数字字符引用（&#NNN; 十进制 / &#xNNN; 十六进制）。
        // ASCII 范围内的 NCR（< 0x80）按 HTML 规范保留转义形式，
        // 命名实体（&lt; &gt; 等）也不在替换范围内，由 Sanitizer 维持其安全语义。
        StringBuilder out = new StringBuilder(sanitized.length());
        int i = 0;
        int n = sanitized.length();
        while (i < n) {
            char c = sanitized.charAt(i);
            if (c == '&' && i + 1 < n && sanitized.charAt(i + 1) == '#') {
                int semi = sanitized.indexOf(';', i + 2);
                if (semi > i + 2 && semi - i <= 10) {
                    String body = sanitized.substring(i + 2, semi);
                    int cp = parseNumericReference(body);
                    if (cp >= 0x80 && cp <= Character.MAX_CODE_POINT) {
                        out.appendCodePoint(cp);
                        i = semi + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /**
     * 解析数字字符引用 body 部分（不含 {@code &#} 前缀和 {@code ;} 后缀）。
     *
     * <p>支持十进制（如 {@code 65279}）与十六进制（{@code xff0c} / {@code Xff0c}）。
     * 解析失败返回 {@code -1}。</p>
     */
    private static int parseNumericReference(String body) {
        if (body.isEmpty()) return -1;
        try {
            char first = body.charAt(0);
            if (first == 'x' || first == 'X') {
                if (body.length() < 2) return -1;
                return Integer.parseInt(body.substring(1), 16);
            }
            return Integer.parseInt(body, 10);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
