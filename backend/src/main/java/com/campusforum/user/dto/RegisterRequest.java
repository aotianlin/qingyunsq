package com.campusforum.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @Size(min = 6, max = 32, message = "密码长度 6-32 位")
    private String password;

    @Size(max = 32, message = "学号最长 32 位")
    private String studentNo;

    @NotBlank(message = "昵称不能为空")
    @Size(max = 64, message = "昵称最长 64 位")
    private String nickname;
}
