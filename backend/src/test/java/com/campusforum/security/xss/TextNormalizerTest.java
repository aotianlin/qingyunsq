package com.campusforum.security.xss;

import com.campusforum.infra.sanitize.TextNormalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TextNormalizer} 单元测试。
 *
 * <p>对应 bugfix.md 漏洞 27：验证敏感词预处理对全角 / 零宽 / 大小写差异的归并能力。</p>
 */
class TextNormalizerTest {

    @Test
    @DisplayName("zeroWidth_isStripped：U+200B 等零宽字符被剥离")
    void zeroWidth_isStripped() {
        // "测\u200B试" 与 "测试" 归一后必须相等
        String input = "测\u200B试";
        assertThat(TextNormalizer.normalize(input)).isEqualTo("测试");

        // 其他零宽字符同样剥离
        assertThat(TextNormalizer.normalize("a\u200Cb")).isEqualTo("ab");
        assertThat(TextNormalizer.normalize("a\u200Db")).isEqualTo("ab");
        assertThat(TextNormalizer.normalize("\uFEFFx")).isEqualTo("x");
        assertThat(TextNormalizer.normalize("a\u2060b")).isEqualTo("ab");
    }

    @Test
    @DisplayName("fullWidth_isConvertedToHalf：全角 ASCII / 全角空格转半角并小写")
    void fullWidth_isConvertedToHalf() {
        // 全角 ABC → abc
        assertThat(TextNormalizer.normalize("ＡＢＣ")).isEqualTo("abc");
        // 全角空格转半角
        assertThat(TextNormalizer.normalize("a\u3000b")).isEqualTo("a b");
        // 混合：全角数字 / 标点
        assertThat(TextNormalizer.normalize("Ｔｅｓｔ１２３")).isEqualTo("test123");
    }

    @Test
    @DisplayName("mixedCase_lowercased：大小写统一为小写")
    void mixedCase_lowercased() {
        assertThat(TextNormalizer.normalize("Hello")).isEqualTo("hello");
        assertThat(TextNormalizer.normalize("ABC")).isEqualTo("abc");
        // 中文字符无大小写概念，保持原样
        assertThat(TextNormalizer.normalize("测试TEST")).isEqualTo("测试test");
    }

    @Test
    @DisplayName("null_returnsEmpty：null 输入返回空串而非 NPE")
    void null_returnsEmpty() {
        assertThat(TextNormalizer.normalize(null)).isEqualTo("");
    }

    @Test
    @DisplayName("idempotent：normalize(normalize(x)) == normalize(x)")
    void idempotent() {
        // 使用一段含全部归一化点的复杂输入
        String raw = "Ｈello\u200B 测\uFEFF试 ＴＥＳＴ\u3000End";
        String once = TextNormalizer.normalize(raw);
        String twice = TextNormalizer.normalize(once);
        assertThat(twice).isEqualTo(once);
    }
}
