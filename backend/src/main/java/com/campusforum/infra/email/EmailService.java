package com.campusforum.infra.email;

/**
 * 邮件发送服务接口
 */
public interface EmailService {

    /**
     * 发送密码重置邮件
     *
     * @param toEmail 收件人邮箱
     * @param resetToken 重置令牌
     */
    void sendResetEmail(String toEmail, String resetToken);
}
