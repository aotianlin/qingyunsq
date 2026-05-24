package com.campusforum.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.infra.email.EmailCodeScene;
import com.campusforum.infra.email.EmailProperties;
import com.campusforum.infra.email.EmailService;
import com.campusforum.infra.security.TrustedProxyResolver;
import com.campusforum.tenant.TenantContext;
import com.campusforum.user.domain.User;
import com.campusforum.user.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 邮箱验证码服务：负责注册 / 登录 / 找回密码场景的验证码下发与消费。
 *
 * <p>本类是 bugfix.md 漏洞 11、漏洞 16 的修复重点：</p>
 * <ul>
 *   <li><b>fail-closed</b>：Redis 异常时<strong>拒绝服务</strong>而不是放行。原实现 {@code isRateLimited}
 *       捕获到 Redis 不可用即 {@code return false}，相当于让攻击者借助 Redis 抖动绕过频率限制
 *       做邮件轰炸；现在统一抛 {@link ErrorCode#SERVICE_UNAVAILABLE}。</li>
 *   <li><b>常量时间比较</b>：{@link #verifyAndConsume(long, String, EmailCodeScene, String)} 使用
 *       {@link MessageDigest#isEqual(byte[], byte[])} 替代 {@code String.equals}，避免短路比较带来的
 *       时序泄漏，使攻击者无法借响应时间逐位猜测正确验证码。</li>
 *   <li><b>IP 维度计数</b>：在邮箱维度限流之外新增"单 IP 每分钟最多 3 次"硬上限，
 *       防止攻击者借大量随机邮箱在同一 IP 上压测 SMTP 出口。</li>
 * </ul>
 *
 * <p>需要注意：邮箱命中"不存在 / 未启用"的静默 return 分支不计入 IP 维度，
 * 否则攻击者可借不存在邮箱故意刷高 IP 桶反制正常用户；只在确认要发送邮件时才计数。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationCodeService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int CODE_BOUND = 1_000_000;
    private static final int CODE_DIGITS = 6;

    /** 单 IP 每分钟最多发送邮箱验证码次数（漏洞 16）。 */
    private static final int IP_RATE_MAX_PER_MINUTE = 3;

    /** IP 维度限流窗口（分钟）。 */
    private static final int IP_RATE_WINDOW_MINUTES = 1;

    private final StringRedisTemplate stringRedisTemplate;
    private final EmailService emailService;
    private final EmailProperties emailProperties;
    private final UserMapper userMapper;
    private final TrustedProxyResolver trustedProxyResolver;
    private final HttpServletRequest httpRequest;

    /**
     * 按租户、用途、邮箱发送验证码。
     * 注册场景拒绝已注册邮箱，登录/找回密码场景统一返回以降低邮箱枚举风险。
     *
     * <p>调用顺序约定：
     * <ol>
     *   <li>邮箱合法性 / 注册占用校验（避免攻击者借不存在邮箱刷 IP 桶）；</li>
     *   <li>IP 维度限流（漏洞 16，每分钟 ≤ 3 次）；</li>
     *   <li>邮箱维度限流（漏洞 11，fail-closed）；</li>
     *   <li>实际下发邮件。</li>
     * </ol>
     * </p>
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

        // 已确认要发送邮件后再做 IP 维度计数，避免攻击者借不存在邮箱刷 IP 桶反制正常用户
        String clientIp = trustedProxyResolver.resolve(httpRequest);
        checkAndIncrementIpRate(clientIp, scene);

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

    /**
     * 校验并消费验证码。
     *
     * <p>修复漏洞 11 时序泄漏：使用 {@link MessageDigest#isEqual(byte[], byte[])} 做常量时间比较，
     * 避免 {@code String.equals} 在首字符不同即短路返回带来的时序差异，使攻击者无法借响应时间
     * 逐位猜测验证码。比较失败 / 长度不一致 / 验证码不存在 / 输入为空都返回相同的"无效或已过期"
     * 错误信息，保持错误响应不可区分。</p>
     */
    public void verifyAndConsume(long tenantId, String email, EmailCodeScene scene, String inputCode) {
        String normalizedEmail = normalizeEmail(email);
        String key = codeKey(tenantId, scene, normalizedEmail);
        String stored = stringRedisTemplate.opsForValue().get(key);
        if (stored == null || inputCode == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "验证码无效或已过期");
        }
        // 常量时间比较：MessageDigest.isEqual 对长度不一致与首字节不一致都耗费相同时间，
        // 避免攻击者借响应时间差异侧信道猜测正确验证码
        byte[] a = stored.getBytes(StandardCharsets.UTF_8);
        byte[] b = inputCode.trim().getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(a, b)) {
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

    /**
     * 邮箱维度限流检查，<strong>fail-closed</strong>。
     *
     * <p>修复漏洞 11：原实现在 Redis 不可用时仅打 WARN 即 {@code return false} 放行，
     * 攻击者只需触发 Redis 抖动即可绕过频率限制做邮件轰炸；现在 Redis 异常时一律抛
     * {@link ErrorCode#SERVICE_UNAVAILABLE}，由全局异常处理器返回 503，宁可拒绝服务也不放行。</p>
     *
     * <p>{@link NumberFormatException} 仍按"已限流"处理（保留现状）：被污染的计数器
     * 不能被解析为整数时倾向保守拒绝。</p>
     */
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
            log.error("邮箱码限流检查失败 (fail-closed): {}", e.getMessage());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
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

    /**
     * IP 维度限流：单 IP 每分钟最多 {@value #IP_RATE_MAX_PER_MINUTE} 次邮箱验证码请求（漏洞 16）。
     *
     * <p>用 Redis {@code INCR} + 首次设置 1 分钟 TTL 实现滑动窗口；{@link BusinessException}
     * 直接向上抛由调用方处理，其余 Redis 异常 fail-closed 抛 503。</p>
     *
     * @param ip    解析后的客户端真实 IP（已经过 {@link TrustedProxyResolver} 处理，不接受伪造头）
     * @param scene 邮箱码用途，与 IP 共同组成 Redis key 区分注册 / 登录 / 找回
     */
    private void checkAndIncrementIpRate(String ip, EmailCodeScene scene) {
        String key = "email_code_rate_ip:" + ip + ":" + scene.name().toLowerCase(Locale.ROOT);
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                stringRedisTemplate.expire(key, IP_RATE_WINDOW_MINUTES, TimeUnit.MINUTES);
            }
            if (count != null && count > IP_RATE_MAX_PER_MINUTE) {
                throw new BusinessException(ErrorCode.RATE_LIMITED.getCode(), "请求过于频繁");
            }
        } catch (BusinessException e) {
            // 自身抛出的限流异常透传，不被外层 fail-closed 分支吞掉
            throw e;
        } catch (Exception e) {
            log.error("IP 维度限流检查失败 (fail-closed): {}", e.getMessage());
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
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
