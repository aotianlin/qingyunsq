package com.campusforum.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PostAiCardVO {
    private String tldr;
    private String audience;
    private String valueType;
    private Integer readMinutes;
    private String commentConsensus;
    private String commentDisputes;
    private Long hotCommentId;
    private String hotCommentExcerpt;
    private List<String> highlights;
}
