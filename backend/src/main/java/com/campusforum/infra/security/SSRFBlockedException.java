package com.campusforum.infra.security;

/**
 * 当出站 HTTP 请求被识别为指向内网/本机/链路本地地址时抛出。
 *
 * <p>由 {@code PrivateNetworkValidator} 与 {@code SafeHttpClient} 在校验阶段或连接阶段抛出，
 * 用于拦截 DNS 重绑定与 SSRF 攻击。</p>
 */
public class SSRFBlockedException extends RuntimeException {

    public SSRFBlockedException(String message) {
        super(message);
    }

    public SSRFBlockedException(String message, Throwable cause) {
        super(message, cause);
    }
}
