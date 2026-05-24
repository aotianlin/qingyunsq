package com.campusforum.sensitive.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 敏感词实体。
 *
 * <p>对应 bugfix.md 漏洞 27 / T8.5：早期实现仅做 {@code String.contains} 比较，
 * 攻击者可借全角 / 零宽 / 大小写差异绕过。新增 {@link #isRegex} 字段后：</p>
 * <ul>
 *   <li>普通词条（{@code isRegex=false}，默认值）：在归一化（NFKC + 移除零宽 +
 *       全角转半角 + 小写）后做 {@code String.contains} 比较；</li>
 *   <li>正则词条（{@code isRegex=true}）：归一化后用 {@link java.util.regex.Pattern}
 *       在内容上 {@code find()}。管理员需在录入时充分测试避免 ReDoS。</li>
 * </ul>
 */
@Data
@TableName("sensitive_words")
public class SensitiveWord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** 敏感词字面量或正则表达式，含义取决于 {@link #isRegex}。 */
    private String word;
    /** 风险等级：1 提示 / 2 疑似 / 3 违规。 */
    private Integer level;
    /**
     * 是否将 {@link #word} 解释为正则表达式。
     *
     * <p>默认 {@code false}，与历史普通词条兼容。{@code true} 时由
     * {@code SensitiveWordService.getRiskLevel} 走 {@link java.util.regex.Pattern}
     * 匹配；规则录入页面需提示管理员先在测试用例上确认匹配范围。</p>
     */
    private Boolean isRegex;
    private LocalDateTime createdAt;
}
