package com.campusforum.sensitive.service;

import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.infra.sanitize.TextNormalizer;
import com.campusforum.sensitive.domain.SensitiveWord;
import com.campusforum.sensitive.mapper.SensitiveWordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.tenant.TenantContext;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
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
 * <p><b>性能与稳健性加固</b>：{@code getRiskLevel} 是发帖 / 评论 / 私信写入的热路径，
 * 早期实现每次都全表查库并对每个正则词条重新 {@code Pattern.compile}、对每个词条重新归一化。
 * 现引入按租户的 Caffeine 短 TTL 缓存（{@link #COMPILED_CACHE}），把"归一化后的普通词 +
 * 预编译的正则 Pattern"缓存起来，增删词条时 {@link #evict()} 失效，既省去重复 DB 查询，
 * 也避免每条 UGC 都重新编译正则。</p>
 *
 * <p>失败容忍：管理员录入的非法正则在编译阶段（{@code add} 时优先校验，缓存构建时兜底）
 * 抛 {@link PatternSyntaxException}，本服务捕获后跳过该条，避免一条错条拖垮全站
 * 风控判定；同时通过日志告警。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveWordService {

    /** 单个正则词条允许的最大长度，超过则视为非法，降低 ReDoS 风险面。 */
    private static final int MAX_REGEX_LENGTH = 200;

    private final SensitiveWordMapper mapper;

    /**
     * 按租户缓存"预处理后的敏感词集合"。
     *
     * <p>TTL 60s + 最大 256 个租户条目：敏感词低频变更、高频读取，短 TTL 既能显著降低
     * 热路径的 DB 压力与正则编译开销，又能在管理员增删词后较快自然过期；同时 {@code add} /
     * {@code delete} 会主动 {@link #evict()} 当前租户条目，保证变更即时可见。</p>
     */
    private final LoadingCache<Long, List<CompiledWord>> COMPILED_CACHE = Caffeine.newBuilder()
            .maximumSize(256)
            .expireAfterWrite(Duration.ofSeconds(60))
            .build(this::loadCompiledWords);

    /**
     * 预处理后的敏感词：普通词条持有归一化后的字面量，正则词条持有预编译的 {@link Pattern}。
     * 避免在 {@code getRiskLevel} 热路径上对每条内容重复归一化 / 重复编译正则。
     */
    private record CompiledWord(int level, String normalizedWord, Pattern regex) {
        boolean matches(String normalizedContent) {
            if (regex != null) {
                return regex.matcher(normalizedContent).find();
            }
            return !normalizedWord.isEmpty() && normalizedContent.contains(normalizedWord);
        }
    }

    /** 普通词条录入的便捷重载（isRegex=false）。 */
    @Transactional
    public void add(String word, int level) {
        add(word, level, false);
    }

    @Transactional
    public void add(String word, int level, boolean isRegex) {
        if (word == null || word.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "敏感词不能为空");
        }
        // 正则词条在录入时即校验合法性与长度，避免把非法 / 高风险正则写进库后到匹配时才失败
        if (isRegex) {
            if (word.length() > MAX_REGEX_LENGTH) {
                throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(),
                        "正则表达式过长（最多 " + MAX_REGEX_LENGTH + " 字符）");
            }
            try {
                Pattern.compile(TextNormalizer.normalize(word));
            } catch (PatternSyntaxException e) {
                throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "正则表达式非法");
            }
        }
        SensitiveWord sw = new SensitiveWord();
        sw.setWord(word.trim());
        sw.setLevel(Math.max(1, Math.min(3, level)));
        sw.setIsRegex(isRegex);
        Long tid = TenantContext.getTenantId();
        sw.setTenantId(tid != null ? tid : 1L);
        mapper.insert(sw);
        evict();
        log.info("Sensitive word added: regex={}, level={}", isRegex, sw.getLevel());
    }

    @Transactional
    public void delete(Long id) {
        Long tid = TenantContext.getTenantId();
        mapper.delete(new LambdaQueryWrapper<SensitiveWord>()
                .eq(SensitiveWord::getId, id)
                .eq(SensitiveWord::getTenantId, tid != null ? tid : 1L));
        evict();
    }

    public List<SensitiveWord> listAll() {
        Long tid = TenantContext.getTenantId();
        return mapper.selectList(new LambdaQueryWrapper<SensitiveWord>()
                .eq(SensitiveWord::getTenantId, tid != null ? tid : 1L));
    }

    /** 主动失效当前租户的敏感词缓存（增删词条后调用）。 */
    public void evict() {
        Long tid = TenantContext.getTenantId();
        COMPILED_CACHE.invalidate(tid != null ? tid : 1L);
    }

    /**
     * 评估给定内容的最大风险等级。
     *
     * <p>对应 bugfix.md 漏洞 27 / T8.5：内容先经 {@link TextNormalizer#normalize} 归一化，
     * 再与缓存中"预处理后的词条"（普通词归一化字面量 / 预编译正则）做 contains 或 find 匹配。</p>
     *
     * @param content 待检测内容
     * @return 命中的最大 level（0 表示未命中）
     */
    public int getRiskLevel(String content) {
        if (content == null) return 0;
        Long tid = TenantContext.getTenantId();
        List<CompiledWord> words = COMPILED_CACHE.get(tid != null ? tid : 1L);
        if (words.isEmpty()) return 0;
        // 漏洞 27：归一化内容后再比较，避免全角 / 零宽 / 大小写绕过
        String normalizedContent = TextNormalizer.normalize(content);
        int maxLevel = 0;
        for (CompiledWord cw : words) {
            if (cw.matches(normalizedContent)) {
                maxLevel = Math.max(maxLevel, cw.level());
                if (maxLevel >= 3) break; // 已达最高等级，无需继续
            }
        }
        return maxLevel;
    }

    /**
     * 缓存加载器：把某租户的敏感词条目预处理为 {@link CompiledWord}。
     * 普通词条预归一化；正则词条预编译（非法 / 超长正则跳过并 WARN，不阻断）。
     */
    private List<CompiledWord> loadCompiledWords(Long tenantId) {
        List<SensitiveWord> raw = mapper.selectList(new LambdaQueryWrapper<SensitiveWord>()
                .eq(SensitiveWord::getTenantId, tenantId));
        List<CompiledWord> compiled = new ArrayList<>(raw.size());
        for (SensitiveWord sw : raw) {
            String word = sw.getWord();
            if (word == null || word.isEmpty()) continue;
            String normalizedWord = TextNormalizer.normalize(word);
            if (normalizedWord.isEmpty()) continue;
            int level = sw.getLevel() != null ? sw.getLevel() : 1;
            if (Boolean.TRUE.equals(sw.getIsRegex())) {
                if (word.length() > MAX_REGEX_LENGTH) {
                    log.warn("敏感词正则过长被跳过 id={}, length={}", sw.getId(), word.length());
                    continue;
                }
                try {
                    compiled.add(new CompiledWord(level, normalizedWord,
                            Pattern.compile(normalizedWord)));
                } catch (PatternSyntaxException e) {
                    // 非法正则不阻断主流程，仅 WARN 提示管理员去后台修正
                    log.warn("敏感词正则非法 id={}: {}", sw.getId(), e.getMessage());
                }
            } else {
                compiled.add(new CompiledWord(level, normalizedWord, null));
            }
        }
        return compiled;
    }
}
