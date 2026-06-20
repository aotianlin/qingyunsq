package com.campusforum.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GithubDeviceLoginRequest {

    @NotBlank(message = "GitHub device code 不能为空")
    private String deviceCode;
}
