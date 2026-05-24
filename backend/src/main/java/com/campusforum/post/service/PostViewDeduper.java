package com.campusforum.post.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 帖子浏览计数去重器（任务 T5.5 / 漏洞 21）。
 *
 * <p><b>背景</b>：原 {@link PostService#viewPost(Long)} 每次处理 {@code GET /api/v1/posts/{id}}
 * 都直接调用 {@code postMapper.incrementViewCount(id)}，攻击者只要不断刷新即可线性放大
 * {@code view_count}，污染热度排序与精华推荐。</p>
 *
 * <p><b>策略</b>：以 {@code (postId, userId|ip)} 维度做 30 分钟窗口去重——
 * <ol>
 *   <li>已登录用户：使用 {@code post_view:<postId>:u:<userId>} 作为 Redis key；</li>
 *   <li>未登录用户：使用 {@code post_view:<postId>:ip:<ip>} 作为 Redis key
 *       （IP 必须由 {@link com.campusforum.infra.security.TrustedProxyResolver}
 *       解析得到，否则攻击者可通过伪造 {@code X-Forwarded-For} 绕过去重）。</li>
 * </ol>
 * Redis SETNX 成功才视为 "本窗口内首次浏览"，调用方应据此决定是否真正递增 view_count；
 * 30 分钟窗口在 "压住批量刷数" 与 "正常用户感知不到计数停滞" 之间取折中。</p>
 *
 * <p><b>fail-open 策略</b>：当 Redis 短暂不可用时，本组件不应阻塞主业务读取流程，
 * 此时退化为 "允许计入"，至多让攻击者在 Redis 抖动期间略微多刷几次——这相对于
 * 因为缓存抖动让所有 GET 详情接口直接 5xx 显然更可接受。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostViewDeduper {

    /**
     * 单条帖子单 user/ip 的去重窗口（秒）。
     *
     * <p>30 分钟既能压住刷数又不会让正常用户感觉计数停滞；同时与 Sa-Token
     * 4 小时 active-timeout 错峰，确保窗口期内即便用户切换设备也不会重复计入。</p>
     */
    private static final long DEDUP_TTL_SECONDS = 30L * 60L;

    private final StringRedisTemplate redis;

    /**
     * 判断当前请求是否应被计入帖子浏览数。
     *
     * <p>实现采用 Redis {@code SETNX + TTL}（{@link org.springframework.data.redis.core.ValueOperations#setIfAbsent
     * (Object, Object, long, TimeUnit)}）原子设置 key：
     * <ul>
     *   <li>返回 true 表示本去重窗口内首次写入成功，调用方应递增 view_count；</li>
     *   <li>返回 false 表示窗口内已经存在，调用方应跳过递增；</li>
     *   <li>Redis 异常时 fail-open，返回 true，避免主业务被缓存故障拖垮。</li>
     * </ul>
     * 注意：本方法<strong>不判断</strong>「调用方是否管理员 / 是否作者本人」，
     * 这些业务策略放在 {@link PostService#viewPost(Long)} 内决定，本组件保持职责单一。</p>
     *
     * @param postId 帖子 ID，必须非 null
     * @param userId 已登录用户 ID；为 null 时退化到 IP 维度去重
     * @param ip     客户端真实 IP（必须经 {@code TrustedProxyResolver.resolve} 解析），
     *               允许为 null（极端情况下退化为 {@code "unknown"} 桶）
     * @return 当且仅当 SETNX 成功（或 Redis 异常 fail-open）时返回 true
     */
    public boolean shouldCount(long postId, Long userId, String ip) {
        // 优先按 userId 维度去重；未登录场景退化到 IP 维度
        String key = userId != null
                ? "post_view:" + postId + ":u:" + userId
                : "post_view:" + postId + ":ip:" + (ip == null || ip.isBlank() ? "unknown" : ip);
        try {
            Boolean ok = redis.opsForValue().setIfAbsent(
                    key, "1", DEDUP_TTL_SECONDS, TimeUnit.SECONDS);
            // setIfAbsent 在 Spring Data Redis 中 null 表示底层异常 / 命令未执行，
            // 这里按 "未明确成功" 处理，与 Boolean.FALSE 一样不计入；
            // 真正 fail-open 在下面的 catch 分支里完成。
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            // Redis 抖动 / 连接异常时 fail-open，避免详情接口整体 5xx
            log.warn("PostViewDeduper Redis 异常，fail-open 计入浏览：postId={}, err={}",
                    postId, e.getMessage());
            return true;
        }
    }
}
