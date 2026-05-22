package com.campusforum.message.dto;

import com.campusforum.user.dto.PublicUserVO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MessageVO {
    private Long id;
    private Long senderId;
    private Long receiverId;
    private PublicUserVO sender;
    private PublicUserVO receiver;
    private String content;
    private String imageUrl;
    private boolean isRead;
    private LocalDateTime createdAt;
}
