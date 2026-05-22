package com.campusforum.infra.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * 基于 Redis Sorted Set 的滑动窗口限流器（Lua 原子化版）。
 *
 * <p>原实现把 zRemRangeByScore + zCard + zAdd 拆成三个调用，并发下会出现"两个请求都看到 count &lt; max
 * 后各自插入一条记录"的竞争窗口。这里改成单条 Lua 脚本一次完成所有判断与写入，保证语义原子。</p>
 */
@Slf4j
@Component
public class RedisRateLimiter {

    /**
     * Lua 脚本输入：
     *  KEYS[1] = sorted set key
     *  ARGV[1] = nowMillis（请求到达时间，毫秒）
     *  ARGV[2] = windowSeconds
     *  ARGV[3] = maxRequests
     *  ARGV[4] = unique member（避免同一 ms 多次写入冲突）
     *
     * 返回：0 = 允许；&gt;0 = 被拒，需要等待的秒数。
     */
    private static final String LUA_SCRIPT = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local max = tonumber(ARGV[3])
            local member = ARGV[4]
            local windowStart = now - window * 1000
            redis.call('ZREMRANGEBYSCORE', key, 0, windowStart)
            local count = tonumber(redis.call('ZCARD', key) or '0')
            if count >= max then
              local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
              local retry = window
              if oldest and #oldest >= 2 then
                local diffMs = (tonumber(oldest[2]) + window * 1000) - now
                if diffMs > 0 then
                  retry = math.ceil(diffMs / 1000)
                end
              end
              if retry < 1 then retry = 1 end
              return retry
            end
            redis.call('ZADD', key, now, member)
            redis.call('EXPIRE', key, window + 1)
            return 0
            """;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> script;

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.script = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);
    }

    /**
     * 默认 fail-open 限流（普通读路径）。
     *
     * @param key           限流键（如 rate_limit:user:123:/api/v1/posts）
     * @param maxRequests   窗口内最大请求数
     * @param windowSeconds 窗口时间（秒）
     * @return 0 = 允许；&gt;0 = 需等待的秒数
     */
    public long tryAcquire(String key, int maxRequests, int windowSeconds) {
        return tryAcquireInternal(key, maxRequests, windowSeconds, false);
    }

    /**
     * fail-closed 限流（敏感写路径，如登录、注册、忘记密码、AI 接口）。
     *
     * <p>Redis 不可用时返回非零（拒绝），避免攻击者借 Redis 抖动绕过限流。</p>
     */
    public long tryAcquireFailClosed(String key, int maxRequests, int windowSeconds) {
        return tryAcquireInternal(key, maxRequests, windowSeconds, true);
    }

    private long tryAcquireInternal(String key, int maxRequests, int windowSeconds, boolean failClosed) {
        try {
            Long retry = redisTemplate.execute(script, List.of(key),
                    String.valueOf(System.currentTimeMillis()),
                    String.valueOf(windowSeconds),
                    String.valueOf(maxRequests),
                    UUID.randomUUID().toString());
            return retry == null ? 0 : retry;
        } catch (Exception e) {
            if (failClosed) {
                log.error("Rate limiter Redis unavailable on sensitive path, denying request: {}", e.getMessage());
                // 拒绝并提示 30 秒后重试
                return 30L;
            }
            // 普通读路径：fail-open，避免限流器把整个站点拖垮
            log.warn("Rate limiter Redis unavailable, allowing request: {}", e.getMessage());
            return 0;
        }
    }
}
