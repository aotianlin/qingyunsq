package com.campusforum.user.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserVO {

    private Long id;
    private String studentNo;
    private String email;
    private String nickname;
    private String avatarUrl;
    private String bio;
    private String college;
    private String major;
    private String grade;
    private String role;
    private Long points;
    private Integer status;
    private Long tenantId;
    private String tenantCode;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}
