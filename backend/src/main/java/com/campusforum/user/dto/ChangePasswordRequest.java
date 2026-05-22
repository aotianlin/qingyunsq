package com.campusforum.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求 DTO。
 *
 * <p>安全加固（缺陷 1.9）：原接口使用 {@code Map<String, String>} 接收 newPassword
 * 绕过了注册时的强密码校验，攻击者可将密码改成 {@code 12345678} 等弱口令。
 * 这里强制与 {@link RegisterRequest} 相同的密码策略。</p>
 */
@Data
public class ChangePasswordRequest {

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度需 8-64 位")
    @Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d!@#$%^&*()_+\\-=\\[\\]{};':\",.<>?/\\\\|`~]+$",
        message = "密码必须同时包含字母和数字"
    )
    private String newPassword;
}
