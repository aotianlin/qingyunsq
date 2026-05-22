package com.campusforum.test;

import com.campusforum.infra.email.EmailCodeScene;
import com.campusforum.tenant.TenantContext;
import com.campusforum.user.dto.RegisterRequest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class EmailCodeTestUtils {

    private static final String DEFAULT_REGISTER_CODE = "123456";
    private static final long DEFAULT_EXPIRE_MINUTES = 10L;

    private EmailCodeTestUtils() {
    }

    public static void prepareRegisterCode(StringRedisTemplate stringRedisTemplate, RegisterRequest req) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("TenantContext is null while preparing register email code");
        }

        req.setEmailCode(DEFAULT_REGISTER_CODE);
        putEmailCode(stringRedisTemplate, tenantId, EmailCodeScene.REGISTER, req.getEmail(), DEFAULT_REGISTER_CODE);
    }

    public static void putEmailCode(StringRedisTemplate stringRedisTemplate,
                                    long tenantId,
                                    EmailCodeScene scene,
                                    String email,
                                    String code) {
        // 测试直接写入与 EmailVerificationCodeService 相同格式的 Redis key，避免依赖真实邮件发送链路。
        stringRedisTemplate.opsForValue().set(
                emailCodeKey(tenantId, scene, email),
                code,
                DEFAULT_EXPIRE_MINUTES,
                TimeUnit.MINUTES);
    }

    public static String emailCodeKey(long tenantId, EmailCodeScene scene, String email) {
        return "email_code:" + tenantId + ":"
                + scene.name().toLowerCase(Locale.ROOT) + ":"
                + normalizeEmail(email);
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
