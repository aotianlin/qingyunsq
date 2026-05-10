package com.campusforum.notify.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notifications")
public class Notification {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long receiverId;
    private Long senderId;
    private String type;
    private String title;
    private String content;
    private String redirectUrl;
    private Integer isRead;
    private LocalDateTime createdAt;
}
