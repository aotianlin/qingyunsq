package com.campusforum.infra.email;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EmailCodeScene {

    REGISTER("注册验证", "完成账号注册"),
    LOGIN("登录验证", "登录账号"),
    RESET_PASSWORD("找回密码", "重置账号密码");

    private final String subjectLabel;
    private final String actionLabel;
}
