package com.campusforum.ai.dto;

import com.campusforum.ai.service.AiService.ChatMessage;
import lombok.Data;

import java.util.List;

@Data
public class AiRequest {
    private String content;
    private String title;
    private List<ChatMessage> messages;
    private String context;
    private String model;
    private List<String> abilities;
}
