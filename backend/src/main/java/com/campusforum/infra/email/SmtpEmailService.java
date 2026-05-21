package com.campusforum.infra.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final EmailProperties emailProperties;

    @Override
    public void sendResetEmail(String toEmail, String resetToken) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(emailProperties.getFrom());
            helper.setTo(toEmail);
            helper.setSubject(emailProperties.getAppName() + " - 密码重置请求");
            helper.setText(buildHtmlContent(resetToken), true);

            mailSender.send(message);
            log.info("Password reset email sent to: {}", maskEmail(toEmail));
        } catch (MessagingException e) {
            // 记录失败但不抛出异常，防止邮箱枚举
            log.error("Failed to send reset email: {}", e.getMessage());
        }
    }

    private String buildHtmlContent(String resetToken) {
        String resetLink = emailProperties.getResetLinkBase() + "?token=" + resetToken;
        int expireMinutes = emailProperties.getResetTokenExpireMinutes();
        String appName = emailProperties.getAppName();

        return """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"></head>
                <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
                    <div style="background: #f8f9fa; border-radius: 8px; padding: 30px;">
                        <h2 style="color: #333; margin-top: 0;">%s 密码重置</h2>
                        <p style="color: #666; line-height: 1.6;">
                            您好，我们收到了您的密码重置请求。请点击下方按钮重置密码：
                        </p>
                        <div style="text-align: center; margin: 30px 0;">
                            <a href="%s"
                               style="background: #4f46e5; color: white; padding: 12px 30px;
                                      text-decoration: none; border-radius: 6px; font-weight: bold;">
                                重置密码
                            </a>
                        </div>
                        <p style="color: #999; font-size: 14px; line-height: 1.5;">
                            此链接将在 %d 分钟后过期。如果您没有请求重置密码，请忽略此邮件。
                        </p>
                        <hr style="border: none; border-top: 1px solid #eee; margin: 20px 0;">
                        <p style="color: #bbb; font-size: 12px;">
                            此邮件由 %s 系统自动发送，请勿回复。
                        </p>
                    </div>
                </body>
                </html>
                """.formatted(appName, resetLink, expireMinutes, appName);
    }

    /** 脱敏邮箱地址用于日志 */
    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 2) return "***" + email.substring(atIndex);
        return email.substring(0, 2) + "***" + email.substring(atIndex);
    }
}
