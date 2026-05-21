package com.campusforum.infra.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis Sorted Set 的滑动窗口限流器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRateLimiter {

    private final StringRedisTemplate redisTemplate;

    /**
     * 检查是否允许请求通过
     *
     * @param key           限流键（如 rate_limit:user:123:/api/v1/posts）
     * @param maxRequests   窗口内最大请求数
     * @param windowSeconds 窗口时间（秒）
     * @return 剩余秒数（0 表示允许通过，>0 表示需要等待的秒数）
     */
    public long tryAcquire(String key, int maxRequests, int windowSeconds) {
        try {
            long now = System.currentTimeMillis();
            long windowStart = now - (windowSeconds * 1000L);

            ZSetOperations<String, String> zSet = redisTemplate.opsForZSet();

            // 移除窗口外的旧记录
            zSet.removeRangeByScore(key, 0, windowStart);

            // 获取当前窗口内的请求数
            Long count = zSet.zCard(key);
            if (count != null && count >= maxRequests) {
                // 超限，计算需要等待的时间
                long retryAfter = windowSeconds - ((now - windowStart) / 1000);
                return Math.max(1, retryAfter);
            }

            // 添加当前请求
            zSet.add(key, UUID.randomUUID().toString(), now);
            redisTemplate.expire(key, windowSeconds + 1, TimeUnit.SECONDS);

            return 0; // 允许通过
        } catch (Exception e) {
            // Redis 不可用时 fail-open
            log.warn("Rate limiter Redis unavailable, allowing request: {}", e.getMessage());
            return 0;
        }
    }
}
