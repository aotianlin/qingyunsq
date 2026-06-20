package com.campusforum.qa.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class QaQuestionVO {
    private Long id;
    private Long postId;
    private Boolean isSolved;
    private Long acceptedCommentId;
    private LocalDateTime solvedAt;
}
