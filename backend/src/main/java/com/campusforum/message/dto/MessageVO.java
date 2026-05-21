package com.campusforum.message.dto;

import com.campusforum.user.dto.UserVO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MessageVO {
    private Long id;
    private Long senderId;
    private Long receiverId;
    private UserVO sender;
    private UserVO receiver;
    private String content;
    private String imageUrl;
    private boolean isRead;
    private LocalDateTime createdAt;
}
