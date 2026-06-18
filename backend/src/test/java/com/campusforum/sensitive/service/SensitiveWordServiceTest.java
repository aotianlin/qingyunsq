package com.campusforum.sensitive.service;

import com.campusforum.sensitive.domain.SensitiveWord;
import com.campusforum.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * 敏感词服务测试。
 *
 * <p>2026-06-01 spec security-audit-hardening：原 {@code sensitive_words} 表
 * 在 {@code TENANT_IGNORE_TABLES} 中，故测试线程不需要租户上下文。
 * T6.1 / T8.5 把它移出忽略名单后，MyBatis-Plus 拦截器会强制注入 tenant_id，
 * 测试线程需要在每个 {@code @BeforeEach} 显式设置 TenantContext，
 * {@code @AfterEach} 清理避免线程污染。</p>
 */
@SpringBootTest
class SensitiveWordServiceTest {

    @Autowired
    private SensitiveWordService service;

    @BeforeEach
    void setUpTenantContext() {
        // 默认租户 1（与 application-dev/test 保持一致）
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void shouldAddAndList() {
        service.add("test-" + System.currentTimeMillis(), 2);
        List<SensitiveWord> all = service.listAll();
        assertThat(all).isNotEmpty();
    }

    @Test
    void shouldGetRiskLevel() {
        String word = "badword-" + System.currentTimeMillis();
        service.add(word, 3);
        int level = service.getRiskLevel("this contains " + word + " inside");
        assertThat(level).isEqualTo(3);
    }

    @Test
    void shouldReturnMaxLevel() {
        String w1 = "low-" + System.currentTimeMillis();
        String w2 = "high-" + System.currentTimeMillis();
        service.add(w1, 1);
        service.add(w2, 3);
        int level = service.getRiskLevel("text with " + w1 + " and " + w2);
        assertThat(level).isEqualTo(3);
    }

    @Test
    void shouldReturnZeroForCleanContent() {
        int level = service.getRiskLevel("clean content nothing bad");
        assertThat(level).isEqualTo(0);
    }

    @Test
    void shouldDeleteWord() {
        String word = "del-" + System.currentTimeMillis();
        service.add(word, 1);
        List<SensitiveWord> all = service.listAll();
        SensitiveWord added = all.stream().filter(w -> w.getWord().equals(word)).findFirst().orElseThrow();
        service.delete(added.getId());
        List<SensitiveWord> after = service.listAll();
        assertThat(after.stream().noneMatch(w -> w.getWord().equals(word))).isTrue();
    }

    // ========== 回归：isRegex 录入与校验（API 缺口修复 + 缓存路径） ==========

    @Test
    void shouldAddRegexWordAndMatch() {
        // 正则词条：以 "诈骗-" + 时间戳开头后接任意数字
        String prefix = "fraud" + System.currentTimeMillis();
        service.add(prefix + "\\d{2,}", 3, true);

        // 命中：前缀后跟 2 位以上数字
        assertThat(service.getRiskLevel("内容含 " + prefix + "123 字样")).isEqualTo(3);
        // 不命中：前缀后无数字
        assertThat(service.getRiskLevel("内容仅含 " + prefix)).isEqualTo(0);
    }

    @Test
    void shouldRejectIllegalRegexOnAdd() {
        // "[" 是不平衡方括号 → 录入时即应被拒（而非到匹配时才静默跳过）
        assertThatThrownBy(() -> service.add("bad[regex" + System.currentTimeMillis(), 2, true))
                .isInstanceOf(com.campusforum.common.BusinessException.class)
                .hasMessageContaining("正则");
    }

    @Test
    void shouldRejectBlankWordOnAdd() {
        assertThatThrownBy(() -> service.add("   ", 1, false))
                .isInstanceOf(com.campusforum.common.BusinessException.class)
                .hasMessageContaining("不能为空");
    }

    @Test
    void shouldReflectAddAndDeleteImmediatelyViaCacheEvict() {
        // 验证缓存在增删后即时失效：add 后立刻命中，delete 后立刻不命中。
        String word = "cacheword" + System.currentTimeMillis();
        service.add(word, 2);
        assertThat(service.getRiskLevel("x " + word + " y")).isEqualTo(2);

        SensitiveWord added = service.listAll().stream()
                .filter(w -> w.getWord().equals(word)).findFirst().orElseThrow();
        service.delete(added.getId());
        assertThat(service.getRiskLevel("x " + word + " y")).isEqualTo(0);
    }
}
