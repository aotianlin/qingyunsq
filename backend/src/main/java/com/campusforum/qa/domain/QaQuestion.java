package com.campusforum.qa.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("qa_questions")
public class QaQuestion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long postId;
    private Integer isSolved;
    private Long acceptedCommentId;
    private LocalDateTime solvedAt;
}
