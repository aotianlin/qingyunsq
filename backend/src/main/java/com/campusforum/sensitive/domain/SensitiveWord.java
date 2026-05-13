package com.campusforum.sensitive.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sensitive_words")
public class SensitiveWord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String word;
    private Integer level;
    private LocalDateTime createdAt;
}
