package com.campusforum.social.service;

public record GithubTokenPollResult(boolean pending, int retryAfterSeconds, String accessToken) {
}
