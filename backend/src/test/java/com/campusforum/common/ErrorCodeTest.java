package com.campusforum.common;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 任务 T9.5：{@link ErrorCode} 枚举完整性回归测试。
 *
 * <p>本测试守护两个不变量：</p>
 * <ol>
 *   <li>所有错误码值唯一（防止扩展时复制粘贴写错值）；</li>
 *   <li>T9.5 新增的 6 个错误码确实存在（防止后续重构误删）。</li>
 * </ol>
 */
class ErrorCodeTest {

    @Test
    void allCodesUnique() {
        List<Integer> codes = Arrays.stream(ErrorCode.values())
                .map(ErrorCode::getCode)
                .toList();
        Set<Integer> unique = new HashSet<>(codes);
        // 若不唯一，列出所有重复值便于定位
        if (unique.size() != codes.size()) {
            String dup = codes.stream()
                    .filter(c -> codes.stream().filter(c::equals).count() > 1)
                    .distinct()
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
            throw new AssertionError("ErrorCode 出现重复值: " + dup);
        }
        assertThat(unique).hasSameSizeAs(codes);
    }

    @Test
    void t95_newCodes_arePresent() {
        // T9.5 要求新增的 6 个错误码全部存在
        assertThat(ErrorCode.TENANT_MISMATCH.getCode()).isEqualTo(40012);
        assertThat(ErrorCode.DOC_ACCESS_DENIED.getCode()).isEqualTo(40013);
        assertThat(ErrorCode.EXPORT_FORBIDDEN.getCode()).isEqualTo(40014);
        // BATCH_SIZE_EXCEEDED 已存在（值为 40008），任务文档中的 40015 与现有值冲突，
        // 这里以已存在值为准，避免破坏现有调用方
        assertThat(ErrorCode.BATCH_SIZE_EXCEEDED.getCode()).isEqualTo(40008);
        assertThat(ErrorCode.WEAK_CONFIG.getCode()).isEqualTo(50011);
        // AI_SERVICE_UNAVAILABLE 已存在（值为 50001）
        assertThat(ErrorCode.AI_SERVICE_UNAVAILABLE.getCode()).isEqualTo(50001);
    }
}
