package com.campusforum.infra.email;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmtpEmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailProperties emailProperties;
    private SmtpEmailService emailService;

    @BeforeEach
    void setUp() {
        emailProperties = new EmailProperties();
        emailProperties.setFrom("test@campusforum.com");
        emailProperties.setResetLinkBase("http://localhost:3000/reset-password");
        emailProperties.setAppName("TestApp");
        emailProperties.setResetTokenExpireMinutes(30);
        emailService = new SmtpEmailService(mailSender, emailProperties);
    }

    @Test
    void sendResetEmail_success() {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        assertThatNoException().isThrownBy(() ->
                emailService.sendResetEmail("user@example.com", "test-token-123"));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendVerificationCode_success() {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        assertThatNoException().isThrownBy(() ->
                emailService.sendVerificationCode("user@example.com", EmailCodeScene.REGISTER, "123456", 10));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendResetEmail_smtpFailure_doesNotThrow() {
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new RuntimeException("SMTP connection refused"))
                .when(mailSender).send(any(MimeMessage.class));

        // 应该不抛异常（静默失败，防止邮箱枚举）
        assertThatNoException().isThrownBy(() ->
                emailService.sendResetEmail("user@example.com", "test-token-123"));
    }
}
