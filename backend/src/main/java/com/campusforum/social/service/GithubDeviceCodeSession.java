package com.campusforum.social.service;

public record GithubDeviceCodeSession(
        String deviceCode,
        String userCode,
        String verificationUri,
        int expiresIn,
        int interval
) {
}
