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
    private static final String IP_FAIL_KEY_PREFIX = "login_fail_ip:";
    private static final String IP_LOCK_KEY_PREFIX = "login_lock_ip:";

    private final SecurityProperties properties;
    private final StringRedisTemplate redisTemplate;

    /**
     * 登录前调用：若已被锁定，抛出业务异常，外层不再继续校验密码。
     *
     * <p>安全加固（缺陷 1.14）：Redis 不可用时 fail-closed，抛出 503。
     * 原本的 fail-open（直接放行）会被攻击者利用 Redis 抖动绕过登录锁定实施暴力破解。</p>
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
            log.error("Login lockout check failed (fail-closed): {}", e.getMessage());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
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
            // recordFailure 路径保留 fail-open（仅日志告警），避免 Redis 抖动让正常用户登录失败
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

    /**
     * IP 维度的锁定校验（缺陷 1.15 加固）。
     *
     * <p>同邮箱锁定可被恶意输入主动锁住合法用户实施 DoS；引入 IP 维度后，
     * 攻击者从同一 IP 高频试错时按 IP 锁定，对账号维度阈值放宽。</p>
     */
    public void ensureIpNotLocked(String ip) {
        if (!properties.getLoginLockout().isEnabled() || ip == null || ip.isBlank()) return;
        String key = IP_LOCK_KEY_PREFIX + ip;
        try {
            if (redisTemplate.opsForValue().get(key) != null) {
                throw new BusinessException(ErrorCode.RATE_LIMITED.getCode(),
                        "来自该 IP 的登录失败过多，请稍后再试");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("IP lockout check failed (fail-closed): {}", e.getMessage());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 记录 IP 维度的失败次数；超过阈值写入 IP 锁定键。
     */
    public void recordIpFailure(String ip) {
        if (!properties.getLoginLockout().isEnabled() || ip == null || ip.isBlank()) return;
        SecurityProperties.LoginLockout cfg = properties.getLoginLockout();
        String failKey = IP_FAIL_KEY_PREFIX + ip;
        try {
            Long count = redisTemplate.opsForValue().increment(failKey);
            if (count != null && count == 1) {
                redisTemplate.expire(failKey, cfg.getWindowSeconds(), TimeUnit.SECONDS);
            }
            if (count != null && count >= cfg.getIpMaxFailures()) {
                redisTemplate.opsForValue().set(IP_LOCK_KEY_PREFIX + ip, "1",
                        cfg.getIpLockoutSeconds(), TimeUnit.SECONDS);
                log.warn("Login locked by IP: ip={}, failures={}", ip, count);
            }
        } catch (Exception e) {
            log.warn("IP lockout record failed: {}", e.getMessage());
        }
    }

    private String failKey(long tenantId, String email) {
        return FAIL_KEY_PREFIX + tenantId + ":" + email.toLowerCase();
    }

    private String lockKey(long tenantId, String email) {
        return LOCK_KEY_PREFIX + tenantId + ":" + email.toLowerCase();
    }
}
