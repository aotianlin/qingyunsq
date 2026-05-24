package com.campusforum.sensitive.service;

import com.campusforum.infra.sanitize.TextNormalizer;
import com.campusforum.sensitive.domain.SensitiveWord;
import com.campusforum.sensitive.mapper.SensitiveWordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 敏感词风险等级检测服务。
 *
 * <p>对应 bugfix.md 漏洞 27 / T8.5：</p>
 * <ul>
 *   <li>使用 {@link TextNormalizer#normalize(String)} 统一对内容与词条做
 *       "NFKC + 移除零宽 + 全角转半角 + 小写"归一化，让 "测试" / "测試" /
 *       "ｔｅｓｔ" / "TEST" / "测\u200B试" 等变体在同一比较空间命中；</li>
 *   <li>支持 {@link SensitiveWord#getIsRegex()} 为正则表达式（管理员显式开启），
 *       匹配阶段走 {@link Pattern#find()}；</li>
 *   <li>普通词条仍用 {@link String#contains(CharSequence)}，但参与比较的是归一化后的形式，
 *       不会再被简单的全角 / 零宽插入绕过。</li>
 * </ul>
 *
 * <p>失败容忍：管理员录入的非法正则会让 {@code Pattern.compile} 抛
 * {@link PatternSyntaxException}，本服务捕获后跳过该条，避免一条错条拖垮全站
 * 风控判定；同时通过日志告警。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveWordService {

    private final SensitiveWordMapper mapper;

    @Transactional
    public void add(String word, int level) {
        SensitiveWord sw = new SensitiveWord();
        sw.setWord(word);
        sw.setLevel(Math.max(1, Math.min(3, level)));
        Long tid = TenantContext.getTenantId();
        sw.setTenantId(tid != null ? tid : 1L);
        mapper.insert(sw);
        log.info("Sensitive word added: {}", word);
    }

    @Transactional
    public void delete(Long id) {
        Long tid = TenantContext.getTenantId();
        mapper.delete(new LambdaQueryWrapper<SensitiveWord>()
                .eq(SensitiveWord::getId, id)
                .eq(SensitiveWord::getTenantId, tid != null ? tid : 1L));
    }

    public List<SensitiveWord> listAll() {
        Long tid = TenantContext.getTenantId();
        return mapper.selectList(new LambdaQueryWrapper<SensitiveWord>()
                .eq(SensitiveWord::getTenantId, tid != null ? tid : 1L));
    }

    /**
     * 评估给定内容的最大风险等级。
     *
     * <p>对应 bugfix.md 漏洞 27 / T8.5：内容与词条都先经过 {@link TextNormalizer#normalize}
     * 归一化，再做 contains 或 regex find 匹配；非法正则被静默跳过并 WARN。</p>
     *
     * @param content 待检测内容
     * @return 命中的最大 level（0 表示未命中）
     */
    public int getRiskLevel(String content) {
        if (content == null) return 0;
        // 漏洞 27：归一化内容后再比较，避免全角 / 零宽 / 大小写绕过
        String normalizedContent = TextNormalizer.normalize(content);
        int maxLevel = 0;
        for (SensitiveWord sw : listAll()) {
            String word = sw.getWord();
            if (word == null || word.isEmpty()) continue;
            // 词条同样归一化，使录入时的"测试"与内容里的"测試"能命中
            String normalizedWord = TextNormalizer.normalize(word);
            if (Boolean.TRUE.equals(sw.getIsRegex())) {
                try {
                    if (Pattern.compile(normalizedWord).matcher(normalizedContent).find()) {
                        maxLevel = Math.max(maxLevel, sw.getLevel());
                    }
                } catch (PatternSyntaxException e) {
                    // 非法正则不阻断主流程，仅 WARN 提示管理员去后台修正
                    log.warn("敏感词正则非法 id={}, pattern={}: {}",
                            sw.getId(), word, e.getMessage());
                }
            } else if (normalizedContent.contains(normalizedWord)) {
                maxLevel = Math.max(maxLevel, sw.getLevel());
            }
        }
        return maxLevel;
    }
}
