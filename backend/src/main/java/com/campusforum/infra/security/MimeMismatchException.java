package com.campusforum.infra.security;

/**
 * 当上传文件的真实 MIME 类型与扩展名声明不一致时抛出。
 *
 * <p>由 {@code MimeTypeValidator} 在文件上传校验阶段抛出，用于阻断扩展名伪造攻击。</p>
 */
public class MimeMismatchException extends RuntimeException {

    public MimeMismatchException(String message) {
        super(message);
    }
}
