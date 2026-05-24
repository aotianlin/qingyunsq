package com.campusforum.infra.sanitize;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 文本归一化工具（敏感词预处理 + 跨字符集匹配）。
 *
 * <p>对应 bugfix.md 漏洞 27：敏感词早期实现仅做 {@code String.contains}，
 * 攻击者可借全角字符（"测试" → "测試"）/ 零宽空格（"测\\u200B试"）/ 大小写差异
 * 绕过敏感词过滤。本工具在做敏感词比对前把输入统一规范化为
 * "NFKC + 移除零宽 + 全角转半角 + 小写"形式，让"测试" / "测試" / "ｔｅｓｔ" /
 * "TEST" 等变体最终归并到同一个比较空间。</p>
 *
 * <p><b>边界</b>：本工具仅参与敏感词 / 文本相等比较的预处理，
 * 不做 HTML 净化也不参与渲染流程；任何最终回送给前端的内容必须仍走
 * {@link HtmlSanitizerService}。</p>
 */
public final class TextNormalizer {

    /**
     * Unicode 零宽 / 不可见字符集合。
     *
     * <p>包括：</p>
     * <ul>
     *   <li>U+200B 零宽空格（ZWSP）</li>
     *   <li>U+200C 零宽非连接符（ZWNJ）</li>
     *   <li>U+200D 零宽连接符（ZWJ）</li>
     *   <li>U+FEFF 字节序标记（BOM）</li>
     *   <li>U+2060 词联接符（WJ）</li>
     * </ul>
     */
    private static final Pattern ZERO_WIDTH = Pattern.compile(
            "[\\u200B\\u200C\\u200D\\uFEFF\\u2060]");

    private TextNormalizer() {}

    /**
     * 归一化文本：NFKC 兼容分解 → 移除零宽字符 → 全角转半角 → 小写。
     *
     * <p>该方法满足幂等性：对任意 {@code s}，
     * {@code normalize(normalize(s)).equals(normalize(s))}。</p>
     *
     * @param input 原始字符串
     * @return 归一化结果；input 为 {@code null} 时返回空串
     */
    public static String normalize(String input) {
        if (input == null) return "";
        String s = Normalizer.normalize(input, Normalizer.Form.NFKC);
        s = ZERO_WIDTH.matcher(s).replaceAll("");
        s = toHalfWidth(s);
        return s.toLowerCase(Locale.ROOT);
    }

    /**
     * 全角字符转半角。
     *
     * <ul>
     *   <li>{@code 0x3000}（全角空格）→ 半角空格 {@code ' '}；</li>
     *   <li>{@code 0xFF01..0xFF5E}（全角 ASCII 块）→ 减去 {@code 0xFEE0} 得到对应半角 ASCII；</li>
     *   <li>其他字符保持不变。</li>
     * </ul>
     */
    private static String toHalfWidth(String s) {
        char[] chars = s.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == 0x3000) {
                chars[i] = ' ';
            } else if (c >= 0xFF01 && c <= 0xFF5E) {
                chars[i] = (char) (c - 0xFEE0);
            }
        }
        return new String(chars);
    }
}
