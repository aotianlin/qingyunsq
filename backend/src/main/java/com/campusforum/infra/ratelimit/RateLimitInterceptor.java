package com.campusforum.infra.ratelimit;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedisRateLimiter rateLimiter;
    private final RateLimitProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!properties.isEnabled()) return true;

        String path = request.getRequestURI();
        String method = request.getMethod();

        // 排除路径检查
        for (String pattern : properties.getExcludePatterns()) {
            if (matchPath(path, pattern)) return true;
        }

        // 确定限流配置
        String endpointKey = method + " " + path;
        RateLimitProperties.LimitConfig config = properties.getOverrides().get(endpointKey);

        String rateLimitKey;
        if (StpUtil.isLogin()) {
            long userId = StpUtil.getLoginIdAsLong();
            rateLimitKey = "rate_limit:user:" + userId + ":" + path;
            if (config == null) config = properties.getAuthenticated();
        } else {
            String ip = getClientIp(request);
            rateLimitKey = "rate_limit:ip:" + ip + ":" + path;
            if (config == null) config = properties.getAnonymous();
        }

        long retryAfter = rateLimiter.tryAcquire(rateLimitKey, config.getMaxRequests(), config.getWindowSeconds());

        if (retryAfter > 0) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后重试\"}");
            return false;
        }

        return true;
    }

    private boolean matchPath(String path, String pattern) {
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return path.startsWith(prefix);
        }
        return path.equals(pattern);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 取第一个 IP（多级代理情况）
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
