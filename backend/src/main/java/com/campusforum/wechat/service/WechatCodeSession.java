package com.campusforum.wechat.service;

public record WechatCodeSession(String openid, String unionid, String sessionKey) {
}
