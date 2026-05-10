package com.campusforum.notify.dto;

import com.campusforum.user.dto.UserVO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class NotificationVO {
    private Long id;
    private Long receiverId;
    private Long senderId;
    private UserVO sender;
    private String type;
    private String title;
    private String content;
    private String redirectUrl;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
