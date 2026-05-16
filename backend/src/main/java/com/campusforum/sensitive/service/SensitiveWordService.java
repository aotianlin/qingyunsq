package com.campusforum.sensitive.service;

import com.campusforum.sensitive.domain.SensitiveWord;
import com.campusforum.sensitive.mapper.SensitiveWordMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    public int getRiskLevel(String content) {
        if (content == null) return 0;
        List<SensitiveWord> words = listAll();
        int maxLevel = 0;
        for (SensitiveWord sw : words) {
            if (content.contains(sw.getWord())) {
                maxLevel = Math.max(maxLevel, sw.getLevel());
            }
        }
        return maxLevel;
    }
}
