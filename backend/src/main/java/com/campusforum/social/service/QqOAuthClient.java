package com.campusforum.social.service;

import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.infra.security.SafeHttpClient;
import com.campusforum.social.config.SocialLoginProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class QqOAuthClient {

    private static final String USER_INFO_URL = "https://graph.qq.com/user/get_user_info";

    private final SocialLoginProperties properties;

    public QqUserInfo getUserInfo(String openid, String accessToken) {
        if (!StringUtils.hasText(properties.getQq().getAppId())) {
            throw new BusinessException(ErrorCode.WEAK_CONFIG.getCode(), "QQ 登录未配置 AppID");
        }
        if (!StringUtils.hasText(openid) || !StringUtils.hasText(accessToken)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "QQ 登录凭证不能为空");
        }

        URI uri = UriComponentsBuilder.fromHttpUrl(USER_INFO_URL)
                .queryParam("access_token", accessToken)
                .queryParam("oauth_consumer_key", properties.getQq().getAppId())
                .queryParam("openid", openid)
                .build(true)
                .toUri();

        Map<?, ?> body;
        try {
            RestTemplate restTemplate = SafeHttpClient.build(
                    properties.getConnectTimeoutMs(),
                    properties.getReadTimeoutMs());
            body = restTemplate.getForObject(uri, Map.class);
        } catch (RestClientException e) {
            log.warn("QQ user info request failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE.getCode(), "QQ 登录服务暂不可用，请稍后重试");
        }

        if (body == null) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE.getCode(), "QQ 登录服务暂不可用，请稍后重试");
        }

        int ret = body.get("ret") instanceof Number number ? number.intValue() : -1;
        if (ret != 0) {
            log.warn("QQ user info rejected token, ret={}, msg={}", ret, body.get("msg"));
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS.getCode(), "QQ 登录凭证无效或已过期");
        }

        return new QqUserInfo(
                openid,
                asText(body.get("nickname"), "QQ用户"),
                asText(body.get("figureurl_qq_2"), asText(body.get("figureurl_qq_1"), null)));
    }

    private static String asText(Object value, String fallback) {
        if (value == null) return fallback;
        String text = String.valueOf(value);
        return text.isBlank() ? fallback : text;
    }
}
