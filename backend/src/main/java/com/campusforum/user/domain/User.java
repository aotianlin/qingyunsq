package com.campusforum.user.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.campusforum.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("users")
public class User extends BaseEntity {

    private String studentNo;
    private String email;
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private String bio;
    private String college;
    private String major;
    private String grade;
    private String role;
    private Long points;
    private Integer status;
    private LocalDateTime lastLoginAt;
    private String resetToken;
    private LocalDateTime resetTokenExpires;
}
