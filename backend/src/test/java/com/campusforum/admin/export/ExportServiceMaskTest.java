package com.campusforum.admin.export;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 任务 T8.6 / 漏洞 13：邮箱与学号掩码工具函数单测（maskEmail / maskStudentNo）。
 *
 * <p>无 Spring 上下文，无 DB 依赖；可在任意环境直接 {@code mvn -Dtest=ExportServiceMaskTest test}。</p>
 */
class ExportServiceMaskTest {

    @Test
    void maskEmail_format_keepsFirstCharAndSuffix() {
        // 常规邮箱：首字符 + *** + @域名
        assertThat(ExportService.maskEmail("alice@example.edu"))
                .isEqualTo("a***@example.edu");
        assertThat(ExportService.maskEmail("a@b.com")).isEqualTo("a***@b.com");
    }

    @Test
    void maskEmail_emptyOrNull_returnedAsIs() {
        assertThat(ExportService.maskEmail(null)).isNull();
        assertThat(ExportService.maskEmail("")).isEmpty();
    }

    @Test
    void maskEmail_invalidWithoutAt_returnsTripleStar() {
        // 无 @ 视为脏数据，整体掩盖避免泄漏
        assertThat(ExportService.maskEmail("not-an-email")).isEqualTo("***");
    }

    @Test
    void maskEmail_atAtFirstChar_doesNotLeakChar() {
        // @abc.com 异常数据：首位即 @，掩码必须不暴露任何字符
        assertThat(ExportService.maskEmail("@abc.com")).isEqualTo("***@abc.com");
    }

    @Test
    void maskStudentNo_format_keepsPrefix4AndLast1() {
        // 8 位学号：保留前 4 + *** + 末 1
        assertThat(ExportService.maskStudentNo("20239876")).isEqualTo("2023***6");
        assertThat(ExportService.maskStudentNo("2024090112345"))
                .isEqualTo("2024***5");
    }

    @Test
    void maskStudentNo_shortInput_keepsOnlyFirst() {
        // ≤5 位的脏数据：仅留首位 + ***
        assertThat(ExportService.maskStudentNo("12345")).isEqualTo("1***");
        assertThat(ExportService.maskStudentNo("12")).isEqualTo("1***");
    }

    @Test
    void maskStudentNo_emptyOrNull_returnedAsIs() {
        assertThat(ExportService.maskStudentNo(null)).isNull();
        assertThat(ExportService.maskStudentNo("")).isEmpty();
    }

    @Test
    void maxRowsConstant_is50000() {
        // 任务 T8.6 要求 MAX_ROWS=50_000，固化在常量上以防回归
        assertThat(ExportService.MAX_ROWS).isEqualTo(50_000);
    }
}
