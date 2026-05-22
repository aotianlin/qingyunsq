package com.campusforum.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AiResponse {
    private String summary;
    private Integer riskLevel;
    private String riskReason;
    private List<String> tags;
    private String reply;
    private List<AiCitation> citations;
}
