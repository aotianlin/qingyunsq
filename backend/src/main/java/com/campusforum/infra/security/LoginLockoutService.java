package com.campusforum.infra.security;

import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 登录失败锁定：基于 Redis 的滑动计数。
 *
 * <p>失败键和锁定键分两条：</p>
 * <ul>
 *   <li>{@code login_fail:{tenant}:{email}}：累计失败次数，TTL = windowSeconds；</li>
 *   <li>{@code login_lock:{tenant}:{email}}：锁定标记，TTL = lockoutSeconds，存在即拒绝。</li>
 * </ul>
 *
 * <p>Redis 不可用时按 fail-open 处理（业务可用性优先），但写日志告警。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginLockoutService {

    private static final String FAIL_KEY_PREFIX = "login_fail:";
    private static final String LOCK_KEY_PREFIX = "login_lock:";

    private final SecurityProperties properties;
    private final StringRedisTemplate redisTemplate;

    /**
     * 登录前调用：若已被锁定，抛出业务异常，外层不再继续校验密码。
     */
    public void ensureNotLocked(long tenantId, String email) {
        if (!properties.getLoginLockout().isEnabled() || email == null) return;
        String key = lockKey(tenantId, email);
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                throw new BusinessException(ErrorCode.RATE_LIMITED.getCode(), "登录失败次数过多，请稍后再试");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Login lockout check failed (fail-open): {}", e.getMessage());
        }
    }

    /**
     * 登录失败后调用：累计失败次数，达到阈值后写入锁定键。
     */
    public void recordFailure(long tenantId, String email) {
        if (!properties.getLoginLockout().isEnabled() || email == null) return;
        String failKey = failKey(tenantId, email);
        SecurityProperties.LoginLockout cfg = properties.getLoginLockout();
        try {
            Long count = redisTemplate.opsForValue().increment(failKey);
            if (count != null && count == 1) {
                redisTemplate.expire(failKey, cfg.getWindowSeconds(), TimeUnit.SECONDS);
            }
            if (count != null && count >= cfg.getMaxFailures()) {
                redisTemplate.opsForValue().set(lockKey(tenantId, email), "1",
                        cfg.getLockoutSeconds(), TimeUnit.SECONDS);
                log.warn("Login locked: tenantId={}, email={}, failures={}", tenantId, email, count);
            }
        } catch (Exception e) {
            log.warn("Login lockout record failed: {}", e.getMessage());
        }
    }

    /**
     * 登录成功后调用：清掉失败计数。
     */
    public void recordSuccess(long tenantId, String email) {
        if (!properties.getLoginLockout().isEnabled() || email == null) return;
        try {
            redisTemplate.delete(failKey(tenantId, email));
            redisTemplate.delete(lockKey(tenantId, email));
        } catch (Exception e) {
            log.warn("Login lockout reset failed: {}", e.getMessage());
        }
    }

    private String failKey(long tenantId, String email) {
        return FAIL_KEY_PREFIX + tenantId + ":" + email.toLowerCase();
    }

    private String lockKey(long tenantId, String email) {
        return LOCK_KEY_PREFIX + tenantId + ":" + email.toLowerCase();
    }
}
