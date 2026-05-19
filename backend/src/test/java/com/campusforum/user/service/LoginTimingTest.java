package com.campusforum.user.service;

import com.campusforum.common.BusinessException;
import com.campusforum.tenant.TenantContext;
import com.campusforum.user.dto.LoginRequest;
import com.campusforum.user.dto.RegisterRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 登录时序攻击缓解验证。
 * <p>
 * 验证 UserService.login() 在"用户不存在"和"密码错误"两种失败场景下的响应时间
 * 统计上无显著差异，确认 DUMMY_BCRYPT_HASH 时序缓解措施有效。
 * <p>
 * <b>Validates: Property 5</b> — Login Timing Indistinguishability
 * <p>
 * 形式化：|mean(rt_user_not_found) - mean(rt_wrong_password)| / max(mean) < 0.10
 */
@SpringBootTest
@Tag("timing")
class LoginTimingTest {

    private static final int N = 100;
    private static final int WARMUP = 10;
    private static final double MAX_RELATIVE_DIFF = 0.10;

    @Autowired
    private UserService userService;

    private String existingEmail;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);

        // 注册一个用户用于"密码错误"场景
        long timestamp = System.nanoTime();
        existingEmail = "timing_test_" + timestamp + "@campusforum.com";

        RegisterRequest req = new RegisterRequest();
        req.setEmail(existingEmail);
        req.setPassword("CorrectPassword123");
        req.setNickname("时序测试用户");
        userService.register(req);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /**
     * 核心断言：两种登录失败场景的平均响应时间相对差异 < 10%。
     * <p>
     * Validates: Property 5
     */
    @Test
    void loginFailureTimingShouldBeIndistinguishable() {
        // Warmup phase: 让 JVM JIT 编译热身，丢弃前 WARMUP 次结果
        for (int i = 0; i < WARMUP; i++) {
            measureLoginNotFound();
            measureLoginWrongPassword();
        }

        // 测量"邮箱不存在"场景的响应时间
        long[] rtNotFound = new long[N];
        for (int i = 0; i < N; i++) {
            rtNotFound[i] = measureLoginNotFound();
        }

        // 测量"邮箱存在但密码错误"场景的响应时间
        long[] rtWrongPassword = new long[N];
        for (int i = 0; i < N; i++) {
            rtWrongPassword[i] = measureLoginWrongPassword();
        }

        double meanNotFound = mean(rtNotFound);
        double meanWrongPassword = mean(rtWrongPassword);
        double maxMean = Math.max(meanNotFound, meanWrongPassword);
        double relativeDiff = Math.abs(meanNotFound - meanWrongPassword) / maxMean;

        System.out.printf("[LoginTimingTest] mean(not_found)=%.2f ms, mean(wrong_pwd)=%.2f ms, " +
                        "relative_diff=%.4f (threshold=%.2f)%n",
                meanNotFound / 1_000_000.0,
                meanWrongPassword / 1_000_000.0,
                relativeDiff,
                MAX_RELATIVE_DIFF);

        assertThat(relativeDiff)
                .as("Login timing relative difference |mean(not_found) - mean(wrong_pwd)| / max(mean) " +
                        "should be < %.0f%% to prevent timing attacks. " +
                        "Actual: not_found=%.2f ms, wrong_pwd=%.2f ms, diff=%.4f",
                        MAX_RELATIVE_DIFF * 100,
                        meanNotFound / 1_000_000.0,
                        meanWrongPassword / 1_000_000.0,
                        relativeDiff)
                .isLessThan(MAX_RELATIVE_DIFF);
    }

    /**
     * 测量一次"邮箱不存在"登录尝试的耗时（纳秒）。
     */
    private long measureLoginNotFound() {
        LoginRequest req = new LoginRequest();
        req.setEmail("nonexistent_" + System.nanoTime() + "@campusforum.com");
        req.setPassword("AnyPassword123");

        long start = System.nanoTime();
        try {
            userService.login(req);
        } catch (BusinessException ignored) {
            // 预期抛出 INVALID_CREDENTIALS
        }
        return System.nanoTime() - start;
    }

    /**
     * 测量一次"邮箱存在但密码错误"登录尝试的耗时（纳秒）。
     */
    private long measureLoginWrongPassword() {
        LoginRequest req = new LoginRequest();
        req.setEmail(existingEmail);
        req.setPassword("WrongPassword_" + System.nanoTime());

        long start = System.nanoTime();
        try {
            userService.login(req);
        } catch (BusinessException ignored) {
            // 预期抛出 INVALID_CREDENTIALS
        }
        return System.nanoTime() - start;
    }

    private static double mean(long[] values) {
        long sum = 0;
        for (long v : values) {
            sum += v;
        }
        return (double) sum / values.length;
    }
}
