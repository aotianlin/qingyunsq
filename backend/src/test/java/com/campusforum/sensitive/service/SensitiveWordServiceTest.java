package com.campusforum.sensitive.service;

import com.campusforum.sensitive.domain.SensitiveWord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class SensitiveWordServiceTest {

    @Autowired
    private SensitiveWordService service;

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
}
