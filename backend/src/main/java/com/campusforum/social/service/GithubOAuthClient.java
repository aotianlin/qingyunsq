package com.campusforum.social.service;

import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.infra.security.SafeHttpClient;
import com.campusforum.social.config.SocialLoginProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GithubOAuthClient {

    private static final String DEVICE_CODE_URL = "https://github.com/login/device/code";
    private static final String ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_URL = "https://api.github.com/user";

    private final SocialLoginProperties properties;

    public GithubDeviceCodeSession startDeviceLogin() {
        ensureConfigured();
        URI uri = UriComponentsBuilder.fromHttpUrl(DEVICE_CODE_URL)
                .queryParam("client_id", properties.getGithub().getClientId())
                .queryParam("scope", "read:user")
                .build(true)
                .toUri();

        Map<?, ?> body = postForm(uri);
        return new GithubDeviceCodeSession(
                asRequiredText(body, "device_code"),
                asRequiredText(body, "user_code"),
                asRequiredText(body, "verification_uri"),
                asInt(body.get("expires_in"), 900),
                asInt(body.get("interval"), 5));
    }

    public GithubTokenPollResult pollToken(String deviceCode) {
        ensureConfigured();
        if (!StringUtils.hasText(deviceCode)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "GitHub 登录凭证不能为空");
        }

        URI uri = UriComponentsBuilder.fromHttpUrl(ACCESS_TOKEN_URL)
                .queryParam("client_id", properties.getGithub().getClientId())
                .queryParam("device_code", deviceCode)
                .queryParam("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .queryParam("client_secret", properties.getGithub().getClientSecret())
                .build(true)
                .toUri();

        Map<?, ?> body = postForm(uri);
        String error = asText(body.get("error"));
        if ("authorization_pending".equals(error)) {
            return new GithubTokenPollResult(true, 0, null);
        }
        if ("slow_down".equals(error)) {
            return new GithubTokenPollResult(true, 5, null);
        }
        if (StringUtils.hasText(error)) {
            log.warn("GitHub device token rejected, error={}", error);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS.getCode(), "GitHub 授权无效或已过期");
        }

        String accessToken = asRequiredText(body, "access_token");
        return new GithubTokenPollResult(false, 0, accessToken);
    }

    public GithubUserInfo getUserInfo(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "GitHub access token 不能为空");
        }

        RestTemplate restTemplate = restTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.set("User-Agent", "CampusForum");

        Map<?, ?> body;
        try {
            body = restTemplate.exchange(URI.create(USER_URL), org.springframework.http.HttpMethod.GET,
                    new HttpEntity<>(headers), Map.class).getBody();
        } catch (RestClientException e) {
            log.warn("GitHub user request failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE.getCode(), "GitHub 登录服务暂不可用，请稍后重试");
        }

        if (body == null) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE.getCode(), "GitHub 登录服务暂不可用，请稍后重试");
        }

        String id = String.valueOf(body.get("id"));
        if (!StringUtils.hasText(id) || "null".equals(id)) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS.getCode(), "GitHub 登录凭证无效或已过期");
        }

        return new GithubUserInfo(
                id,
                asText(body.get("login")),
                asText(body.get("name")),
                asText(body.get("avatar_url")));
    }

    private Map<?, ?> postForm(URI uri) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.set("User-Agent", "CampusForum");
        try {
            return restTemplate().postForObject(uri, new HttpEntity<>(headers), Map.class);
        } catch (RestClientException e) {
            log.warn("GitHub OAuth request failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE.getCode(), "GitHub 登录服务暂不可用，请稍后重试");
        }
    }

    private void ensureConfigured() {
        if (!StringUtils.hasText(properties.getGithub().getClientId())
                || !StringUtils.hasText(properties.getGithub().getClientSecret())) {
            throw new BusinessException(ErrorCode.WEAK_CONFIG.getCode(), "GitHub 登录未配置 Client ID 或 Client Secret");
        }
    }

    private RestTemplate restTemplate() {
        return SafeHttpClient.build(properties.getConnectTimeoutMs(), properties.getReadTimeoutMs());
    }

    private static String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String asRequiredText(Map<?, ?> body, String key) {
        String value = asText(body.get(key));
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE.getCode(), "GitHub 登录服务暂不可用，请稍后重试");
        }
        return value;
    }

    private static int asInt(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }
}
