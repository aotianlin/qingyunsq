package com.campusforum.ai.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiCitation {
    private String type;
    private Long id;
    private String title;
    private String snippet;
    private String url;
}
