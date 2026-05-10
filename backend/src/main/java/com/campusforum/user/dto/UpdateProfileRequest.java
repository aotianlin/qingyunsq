package com.campusforum.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(max = 64, message = "昵称最长 64 位")
    private String nickname;

    @Size(max = 255, message = "头像 URL 最长 255 位")
    private String avatarUrl;

    @Size(max = 255, message = "个人简介最长 255 位")
    private String bio;

    @Size(max = 64, message = "学院最长 64 位")
    private String college;

    @Size(max = 64, message = "专业最长 64 位")
    private String major;

    @Size(max = 8, message = "年级最长 8 位")
    private String grade;
}
