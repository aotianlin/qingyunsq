package com.campusforum.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度需 8-64 位")
    @Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d!@#$%^&*()_+\\-=\\[\\]{};':\",.<>?/\\\\|`~]+$",
        message = "密码必须同时包含字母和数字"
    )
    private String password;

    @NotBlank(message = "验证码不能为空")
    private String emailCode;

    @Size(max = 32, message = "学号最长 32 位")
    private String studentNo;

    @NotBlank(message = "昵称不能为空")
    @Size(max = 32, message = "昵称最长 32 位")
    @Pattern(regexp = "^[\\w\\u4e00-\\u9fa5\\- ]{1,32}$",
            message = "昵称仅允许中英文 / 数字 / 下划线 / 连字符 / 空格")
    private String nickname;
}
