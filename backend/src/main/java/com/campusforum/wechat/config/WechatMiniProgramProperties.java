package com.campusforum.wechat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "wechat.mini-program")
public class WechatMiniProgramProperties {

    private String appId;
    private String appSecret;
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 8000;
}
