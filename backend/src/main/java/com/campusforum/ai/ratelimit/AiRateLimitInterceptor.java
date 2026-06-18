package com.campusforum.ai.ratelimit;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.infra.ratelimit.RedisRateLimiter;
import com.campusforum.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * AI 接口两级限流拦截器。
 *
 * <p>层级 1：per-user/min — 防止单用户短时刷接口（默认 5/min）。
 * <p>层级 2：per-tenant/day — 防止整租户耗光 DeepSeek 预算（默认 1000/day）。
 *
 * <p>实现：复用 {@link RedisRateLimiter} 滑动窗口；任一限流命中即返回 429。
 * <p>未登录请求不在这里拦截（让 Sa-Token 鉴权拦截器接管），避免重复返回 401/429 混淆。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiRateLimitInterceptor implements HandlerInterceptor {

    private final RedisRateLimiter rateLimiter;
    private final AiRateLimitProperties props;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        if (!isCostlyAiEndpoint(req)) {
            return true;
        }
        if (!StpUtil.isLogin()) {
            // 未登录请求 fall-through，让鉴权拦截器返回 401
            return true;
        }
        long userId = StpUtil.getLoginIdAsLong();
        Long tenantId = TenantContext.getTenantId();

        // 1) per-user per-minute
        String userKey = "ai_rate:user:" + userId + ":min";
        long retryAfter = rateLimiter.tryAcquire(userKey, props.getPerUserPerMin(), 60);
        if (retryAfter > 0) {
            log.info("AI rate limit hit (user): userId={}, retryAfter={}s", userId, retryAfter);
            return reject(res, retryAfter,
                    "AI 调用过于频繁（每分钟最多 " + props.getPerUserPerMin() + " 次）");
        }

        // 2) per-tenant per-day
        if (tenantId != null) {
            String tenantKey = "ai_rate:tenant:" + tenantId + ":day";
            retryAfter = rateLimiter.tryAcquire(tenantKey, props.getPerTenantPerDay(), 86400);
            if (retryAfter > 0) {
                log.info("AI rate limit hit (tenant): tenantId={}, retryAfter={}s", tenantId, retryAfter);
                return reject(res, retryAfter,
                        "本租户今日 AI 调用已达上限（" + props.getPerTenantPerDay() + " 次）");
            }
        }

        return true;
    }

    private boolean isCostlyAiEndpoint(HttpServletRequest req) {
        String method = req.getMethod();
        String path = req.getRequestURI();
        if (!"POST".equalsIgnoreCase(method)) {
            return false;
        }
        return path.equals("/api/v1/ai/chat")
                || path.equals("/api/v1/ai/rag-chat")
                || path.equals("/api/v1/ai/summarize")
                || path.equals("/api/v1/ai/moderate")
                || path.equals("/api/v1/ai/tags")
                || path.matches("/api/v1/ai/post-card/[^/]+")
                || path.matches("/api/v1/ai/plugins/[^/]+/invoke")
                || path.matches("/api/v1/ai/conversations/[^/]+/messages");
    }

    private boolean reject(HttpServletResponse res, long retryAfter, String msg) throws IOException {
        res.setStatus(429);
        res.setHeader("Retry-After", String.valueOf(retryAfter));
        res.setContentType("application/json;charset=UTF-8");
        // 错误码 42900 与 GlobalExceptionHandler 风格一致（4xxxx 客户端错误）
        res.getWriter().write("{\"code\":42900,\"message\":\"" + msg + "\",\"data\":null}");
        return false;
    }
}
