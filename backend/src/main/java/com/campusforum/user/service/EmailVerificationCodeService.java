package com.campusforum.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.infra.email.EmailCodeScene;
import com.campusforum.infra.email.EmailProperties;
import com.campusforum.infra.email.EmailService;
import com.campusforum.tenant.TenantContext;
import com.campusforum.user.domain.User;
import com.campusforum.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationCodeService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int CODE_BOUND = 1_000_000;
    private static final int CODE_DIGITS = 6;

    private final StringRedisTemplate stringRedisTemplate;
    private final EmailService emailService;
    private final EmailProperties emailProperties;
    private final UserMapper userMapper;

    /**
     * 按租户、用途、邮箱发送验证码。
     * 注册场景拒绝已注册邮箱，登录/找回密码场景统一返回以降低邮箱枚举风险。
     */
    public void sendCode(String email, EmailCodeScene scene) {
        long tenantId = requireTenantId();
        String normalizedEmail = normalizeEmail(email);

        if (scene == EmailCodeScene.REGISTER && existsByEmail(tenantId, normalizedEmail)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "该邮箱已注册");
        }

        if (scene != EmailCodeScene.REGISTER && findEnabledUser(tenantId, normalizedEmail) == null) {
            log.info("Verification code requested for non-existent email (suppressed), scene={}", scene.name());
            return;
        }

        String rateLimitKey = rateLimitKey(tenantId, scene, normalizedEmail);
        if (isRateLimited(rateLimitKey)) {
            throw new BusinessException(ErrorCode.RATE_LIMITED.getCode(), "验证码发送过于频繁，请稍后再试");
        }

        String code = generateCode();
        stringRedisTemplate.opsForValue().set(
                codeKey(tenantId, scene, normalizedEmail),
                code,
                emailProperties.getCodeExpireMinutes(),
                TimeUnit.MINUTES);
        incrementRateLimit(rateLimitKey);

        emailService.sendVerificationCode(normalizedEmail, scene, code, emailProperties.getCodeExpireMinutes());
    }

    public void verifyAndConsume(String email, EmailCodeScene scene, String inputCode) {
        long tenantId = requireTenantId();
        verifyAndConsume(tenantId, email, scene, inputCode);
    }

    public void verifyAndConsume(long tenantId, String email, EmailCodeScene scene, String inputCode) {
        String normalizedEmail = normalizeEmail(email);
        String key = codeKey(tenantId, scene, normalizedEmail);
        String stored = stringRedisTemplate.opsForValue().get(key);
        if (stored == null || inputCode == null || !stored.equals(inputCode.trim())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "验证码无效或已过期");
        }
        stringRedisTemplate.delete(key);
    }

    private boolean existsByEmail(long tenantId, String email) {
        return userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getTenantId, tenantId)
                .eq(User::getEmail, email)) > 0;
    }

    private User findEnabledUser(long tenantId, String email) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getTenantId, tenantId)
                .eq(User::getEmail, email)
                .eq(User::getStatus, 1));
    }

    private boolean isRateLimited(String key) {
        try {
            String countStr = stringRedisTemplate.opsForValue().get(key);
            if (countStr == null) {
                return false;
            }
            return Integer.parseInt(countStr) >= emailProperties.getRateLimitMaxRequests();
        } catch (NumberFormatException e) {
            return true;
        } catch (Exception e) {
            log.warn("Redis unavailable for email code rate limit check, allowing request: {}", e.getMessage());
            return false;
        }
    }

    private void incrementRateLimit(String key) {
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                stringRedisTemplate.expire(key, emailProperties.getRateLimitWindowMinutes(), TimeUnit.MINUTES);
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for email code rate limit increment: {}", e.getMessage());
        }
    }

    private String codeKey(long tenantId, EmailCodeScene scene, String email) {
        return "email_code:" + tenantId + ":" + scene.name().toLowerCase(Locale.ROOT) + ":" + email;
    }

    private String rateLimitKey(long tenantId, EmailCodeScene scene, String email) {
        return "email_code_rate:" + tenantId + ":" + scene.name().toLowerCase(Locale.ROOT) + ":" + email;
    }

    private String generateCode() {
        return String.format("%0" + CODE_DIGITS + "d", SECURE_RANDOM.nextInt(CODE_BOUND));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private long requireTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("TenantContext is null while sending or verifying email code");
        }
        return tenantId;
    }
}
