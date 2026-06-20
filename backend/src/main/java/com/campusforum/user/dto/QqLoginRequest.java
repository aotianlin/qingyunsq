package com.campusforum.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class QqLoginRequest {

    @NotBlank(message = "QQ openid 不能为空")
    private String openid;

    @NotBlank(message = "QQ access token 不能为空")
    private String accessToken;
}
