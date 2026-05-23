package com.campusforum.ai.ratelimit;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.infra.ratelimit.RedisRateLimiter;
import com.campusforum.tenant.TenantContext;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AiRateLimitInterceptor 单元测试。
 *
 * 覆盖：
 * - 用户超限返回 429 + Retry-After
 * - 租户超限返回 429（在用户未超限的前提下）
 * - 未登录请求 fall-through（不在这里拦）
 * - 正常请求 → 命中两个限流键都未超 → 放行
 */
@ExtendWith(MockitoExtension.class)
class AiRateLimitInterceptorTest {

    @Mock
    private RedisRateLimiter rateLimiter;

    private AiRateLimitProperties props;
    private AiRateLimitInterceptor interceptor;

    @BeforeEach
    void setUp() {
        props = new AiRateLimitProperties();  // 默认 5/min, 1000/day
        interceptor = new AiRateLimitInterceptor(rateLimiter, props);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void preHandle_shouldPassThroughForUnauthenticatedRequest() throws Exception {
        try (MockedStatic<StpUtil> stp = org.mockito.Mockito.mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);

            boolean result = interceptor.preHandle(
                    new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());

            assertThat(result).isTrue();
            // 完全不应触碰 rate limiter
            verify(rateLimiter, never()).tryAcquire(org.mockito.ArgumentMatchers.anyString(), anyInt(), anyInt());
        }
    }

    @Test
    void preHandle_shouldRejectWhenUserPerMinExceeded() throws Exception {
        TenantContext.setTenantId(1L);
        MockHttpServletResponse res = new MockHttpServletResponse();

        try (MockedStatic<StpUtil> stp = org.mockito.Mockito.mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            when(rateLimiter.tryAcquire(eq("ai_rate:user:7:min"), eq(5), eq(60))).thenReturn(42L);

            boolean result = interceptor.preHandle(new MockHttpServletRequest(), res, new Object());

            assertThat(result).isFalse();
            assertThat(res.getStatus()).isEqualTo(429);
            assertThat(res.getHeader("Retry-After")).isEqualTo("42");
            assertThat(res.getContentAsString()).contains("每分钟");
            // 用户超限不应再检查租户限流
            verify(rateLimiter, never()).tryAcquire(eq("ai_rate:tenant:1:day"), anyInt(), anyInt());
        }
    }

    @Test
    void preHandle_shouldRejectWhenTenantPerDayExceeded() throws Exception {
        TenantContext.setTenantId(1L);
        MockHttpServletResponse res = new MockHttpServletResponse();

        try (MockedStatic<StpUtil> stp = org.mockito.Mockito.mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            when(rateLimiter.tryAcquire(eq("ai_rate:user:7:min"), eq(5), eq(60))).thenReturn(0L);
            when(rateLimiter.tryAcquire(eq("ai_rate:tenant:1:day"), eq(1000), eq(86400))).thenReturn(3600L);

            boolean result = interceptor.preHandle(new MockHttpServletRequest(), res, new Object());

            assertThat(result).isFalse();
            assertThat(res.getStatus()).isEqualTo(429);
            assertThat(res.getHeader("Retry-After")).isEqualTo("3600");
            assertThat(res.getContentAsString()).contains("今日");
        }
    }

    @Test
    void preHandle_shouldAllowWhenBothLimitsNotExceeded() throws Exception {
        TenantContext.setTenantId(1L);
        MockHttpServletResponse res = new MockHttpServletResponse();

        try (MockedStatic<StpUtil> stp = org.mockito.Mockito.mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            when(rateLimiter.tryAcquire(eq("ai_rate:user:7:min"), eq(5), eq(60))).thenReturn(0L);
            when(rateLimiter.tryAcquire(eq("ai_rate:tenant:1:day"), eq(1000), eq(86400))).thenReturn(0L);

            boolean result = interceptor.preHandle(new MockHttpServletRequest(), res, new Object());

            assertThat(result).isTrue();
            assertThat(res.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        }
    }

    @Test
    void preHandle_shouldSkipTenantLimitWhenTenantContextMissing() throws Exception {
        // 不设 TenantContext
        MockHttpServletResponse res = new MockHttpServletResponse();

        try (MockedStatic<StpUtil> stp = org.mockito.Mockito.mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            when(rateLimiter.tryAcquire(eq("ai_rate:user:7:min"), eq(5), eq(60))).thenReturn(0L);

            boolean result = interceptor.preHandle(new MockHttpServletRequest(), res, new Object());

            assertThat(result).isTrue();
            // tenant 限流键不应被检查
            verify(rateLimiter, never()).tryAcquire(
                    org.mockito.ArgumentMatchers.startsWith("ai_rate:tenant:"), anyInt(), anyInt());
        }
    }
}
