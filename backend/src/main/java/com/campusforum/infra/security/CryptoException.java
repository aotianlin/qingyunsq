package com.campusforum.infra.security;

/**
 * 加密 / 解密失败时抛出。
 *
 * <p>该异常不应将原始密文或密钥信息附在 message 中，避免日志泄漏。</p>
 */
public class CryptoException extends RuntimeException {

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
