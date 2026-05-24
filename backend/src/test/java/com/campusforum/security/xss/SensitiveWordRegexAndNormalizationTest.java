package com.campusforum.security.xss;

import com.campusforum.sensitive.domain.SensitiveWord;
import com.campusforum.sensitive.mapper.SensitiveWordMapper;
import com.campusforum.sensitive.service.SensitiveWordService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link SensitiveWordService#getRiskLevel(String)} 单元测试。
 *
 * <p>对应 bugfix.md 漏洞 27 / T8.5：</p>
 * <ul>
 *   <li>归一化（NFKC + 零宽剥离 + 全角转半角 + 小写）后能命中绕过变体；</li>
 *   <li>正则词条经 {@link java.util.regex.Pattern#find()} 匹配；</li>
 *   <li>非法正则不阻断主流程，仅跳过该条。</li>
 * </ul>
 */
class SensitiveWordRegexAndNormalizationTest {

    private SensitiveWordMapper mapper;
    private SensitiveWordService service;

    @BeforeEach
    void setUp() {
        mapper = mock(SensitiveWordMapper.class);
        service = new SensitiveWordService(mapper);
        // 单元测试不依赖租户上下文，listAll 内部读 TenantContext.getTenantId()，
        // 但本测试用 mock 直接 stub mapper.selectList 的返回值，无需真正命中 SQL。
        com.campusforum.tenant.TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        com.campusforum.tenant.TenantContext.clear();
    }

    /** 构造一个普通敏感词条目。 */
    private SensitiveWord plain(String word, int level) {
        SensitiveWord sw = new SensitiveWord();
        sw.setId(1L);
        sw.setWord(word);
        sw.setLevel(level);
        sw.setIsRegex(false);
        return sw;
    }

    /** 构造一个正则敏感词条目。 */
    private SensitiveWord regex(String pattern, int level) {
        SensitiveWord sw = new SensitiveWord();
        sw.setId(2L);
        sw.setWord(pattern);
        sw.setLevel(level);
        sw.setIsRegex(true);
        return sw;
    }

    @Test
    @DisplayName("归一化命中：含零宽字符的变体仍能命中普通词条")
    void zeroWidth_variant_isCaught() {
        when(mapper.selectList(ArgumentMatchers.any())).thenReturn(List.of(plain("测试", 2)));

        // 内容里嵌入 U+200B 零宽空格：旧实现 contains 不会命中，新实现归一化后命中
        int level = service.getRiskLevel("这是测\u200B试内容");

        assertThat(level).isEqualTo(2);
    }

    @Test
    @DisplayName("归一化命中：全角字符 / 大小写差异变体仍命中")
    void fullWidth_andCase_variant_isCaught() {
        when(mapper.selectList(ArgumentMatchers.any())).thenReturn(List.of(plain("test", 1)));

        // 全角 TEST → 归一化为 "test" → 命中
        int level1 = service.getRiskLevel("注意：ＴＥＳＴ 内容");
        assertThat(level1).isEqualTo(1);

        // 大写 TEST → 归一化为 "test" → 命中
        int level2 = service.getRiskLevel("注意：TEST 内容");
        assertThat(level2).isEqualTo(1);
    }

    @Test
    @DisplayName("正则匹配：is_regex=true 时按 Pattern.find 命中")
    void regex_word_matches() {
        // 正则：以 "金融" 开头后接任意字符
        when(mapper.selectList(ArgumentMatchers.any())).thenReturn(List.of(regex("金融.{2,}", 3)));

        assertThat(service.getRiskLevel("内容含 金融诈骗 字样")).isEqualTo(3);
        // 正则不命中 → level=0
        assertThat(service.getRiskLevel("内容仅含 金融")).isEqualTo(0);
    }

    @Test
    @DisplayName("非法正则：不阻断主流程，跳过该条")
    void illegalRegex_isSkipped() {
        // "[" 是不平衡的方括号 → Pattern.compile 抛 PatternSyntaxException
        when(mapper.selectList(ArgumentMatchers.any())).thenReturn(List.of(
                regex("[", 3),
                plain("正常词", 1)
        ));

        // 非法正则被跳过；正常词条仍工作
        int level = service.getRiskLevel("含 正常词 的文本");
        assertThat(level).isEqualTo(1);
    }

    @Test
    @DisplayName("空内容 / null：返回 0")
    void emptyOrNull_returnsZero() {
        // null 直接 return 0，不查 mapper
        assertThat(service.getRiskLevel(null)).isEqualTo(0);

        // 空字符串：mapper 仍会被调用，但归一化后不含任何词
        when(mapper.selectList(ArgumentMatchers.any())).thenReturn(List.of(plain("xx", 1)));
        assertThat(service.getRiskLevel("")).isEqualTo(0);
    }

    @Test
    @DisplayName("多条命中：取最大 level")
    void multipleHits_returnsMaxLevel() {
        when(mapper.selectList(ArgumentMatchers.any())).thenReturn(List.of(
                plain("low", 1),
                plain("high", 3),
                plain("mid", 2)
        ));

        assertThat(service.getRiskLevel("low high mid 三个都有")).isEqualTo(3);
    }
}
