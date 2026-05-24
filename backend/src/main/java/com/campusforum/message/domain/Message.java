package com.campusforum.message.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("messages")
public class Message {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long senderId;
    private Long receiverId;
    private String content;
    private String imageUrl;
    private Integer isRead;
    /**
     * AI 风险等级（任务 T8.10 / 漏洞 16）：来自
     * {@code SensitiveWordService.getRiskLevel(content)}，写入私信表用于风控筛选。
     * 0 = 安全，1 = 疑似，2 = 违规。默认 0；NOT NULL 由数据库层保证。
     */
    private Integer aiRiskLevel;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
