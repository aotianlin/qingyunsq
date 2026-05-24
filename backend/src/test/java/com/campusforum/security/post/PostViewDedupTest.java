package com.campusforum.security.post;

import com.campusforum.post.service.PostViewDeduper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PostViewDeduper} 单元测试（任务 T5.5 / 漏洞 21）。
 *
 * <p>覆盖 4 个核心行为：
 * <ol>
 *   <li>首次调用 SETNX 成功 → {@code shouldCount=true} 且写入 30 分钟 TTL；</li>
 *   <li>再次调用 SETNX 返回 false → {@code shouldCount=false}（窗口内已计数）；</li>
 *   <li>Redis 异常 fail-open → {@code shouldCount=true}（不能让缓存抖动让详情接口整体不可用）；</li>
 *   <li>userId / IP 维度生成的 Redis key 形状符合契约（{@code post_view:<id>:u:<uid>} /
 *       {@code post_view:<id>:ip:<ip>}），保证攻击者不能通过伪造 {@code X-Forwarded-For}
 *       让每次请求落入不同桶绕过去重。</li>
 * </ol>
 *
 * <p>测试一律使用 Mockito mock {@link StringRedisTemplate} 与 {@link ValueOperations}，
 * <strong>不启动 Spring 上下文 / 不连接真实 Redis / MySQL</strong>，符合 "纯单元测试" 约束。</p>
 */
class PostViewDedupTest {

    /** 被测对象。 */
    private PostViewDeduper deduper;

    /** Mock 协作者。 */
    private StringRedisTemplate redisTemplate;
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        deduper = new PostViewDeduper(redisTemplate);
    }

    /**
     * 验证属性 1：第一次调用 SETNX 返回 true，方法应返回 true 并以 30 分钟 TTL 写入 key。
     */
    @Test
    @DisplayName("首次调用 SETNX 成功 → shouldCount=true 且 TTL=30 分钟")
    void firstCall_returnsTrue_andSetsKey() {
        when(valueOps.setIfAbsent(anyString(), eq("1"), anyLong(), any(TimeUnit.class)))
                .thenReturn(Boolean.TRUE);

        boolean counted = deduper.shouldCount(100L, 42L, "1.2.3.4");

        assertThat(counted).isTrue();

        // 捕获 SETNX 调用以验证 TTL 与 key 形状
        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Long> ttlCap = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<TimeUnit> unitCap = ArgumentCaptor.forClass(TimeUnit.class);
        verify(valueOps).setIfAbsent(keyCap.capture(), eq("1"), ttlCap.capture(), unitCap.capture());

        assertThat(keyCap.getValue()).isEqualTo("post_view:100:u:42");
        // 30 分钟去重窗口：1800 秒
        assertThat(ttlCap.getValue()).isEqualTo(1800L);
        assertThat(unitCap.getValue()).isEqualTo(TimeUnit.SECONDS);
    }

    /**
     * 验证属性 2：窗口内第二次调用，SETNX 返回 false → 方法返回 false（不计数）。
     * 这是漏洞 21 的核心 fix-checking：刷新无法继续递增 view_count。
     */
    @Test
    @DisplayName("窗口内重复调用 SETNX 返回 false → shouldCount=false（不再计数）")
    void secondCall_returnsFalse() {
        when(valueOps.setIfAbsent(anyString(), eq("1"), anyLong(), any(TimeUnit.class)))
                .thenReturn(Boolean.FALSE);

        boolean counted = deduper.shouldCount(100L, 42L, "1.2.3.4");

        assertThat(counted).isFalse();
    }

    /**
     * 验证属性 3：Redis 不可用时 fail-open 计入浏览，避免详情接口整体 5xx。
     * 这是有意为之的可用性 / 防御性折中：Redis 抖动期间至多让攻击者多刷几次浏览，
     * 远比让所有用户的 GET 详情接口都 5xx 更可接受。
     */
    @Test
    @DisplayName("Redis 抛异常时 fail-open，shouldCount=true（不阻塞主业务）")
    void redisException_failsOpen() {
        when(valueOps.setIfAbsent(anyString(), eq("1"), anyLong(), any(TimeUnit.class)))
                .thenThrow(new RedisConnectionFailureException("simulated redis down"));

        boolean counted = deduper.shouldCount(100L, 42L, "1.2.3.4");

        assertThat(counted).isTrue();
    }

    /**
     * 验证属性 4-A：未登录场景（userId=null）应使用 IP 维度 key，
     * 攻击者不能用同一 IP 多次刷数（key 一致即被去重）。
     */
    @Test
    @DisplayName("userId=null 时使用 IP 维度 key（post_view:<postId>:ip:<ip>）")
    void userIdNull_usesIpKey() {
        when(valueOps.setIfAbsent(anyString(), eq("1"), anyLong(), any(TimeUnit.class)))
                .thenReturn(Boolean.TRUE);

        deduper.shouldCount(7L, null, "1.2.3.4");

        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        verify(valueOps).setIfAbsent(keyCap.capture(), eq("1"), anyLong(), any(TimeUnit.class));
        assertThat(keyCap.getValue()).isEqualTo("post_view:7:ip:1.2.3.4");
    }

    /**
     * 验证属性 4-B：已登录场景应优先按 userId 维度去重，
     * 避免同一用户用 NAT 公共出口 IP 时被多个用户错误共享一个桶。
     */
    @Test
    @DisplayName("userId!=null 时优先使用 userId 维度 key（post_view:<postId>:u:<userId>）")
    void userIdSet_usesUserKey() {
        when(valueOps.setIfAbsent(anyString(), eq("1"), anyLong(), any(TimeUnit.class)))
                .thenReturn(Boolean.TRUE);

        deduper.shouldCount(7L, 42L, "1.2.3.4");

        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        verify(valueOps).setIfAbsent(keyCap.capture(), eq("1"), anyLong(), any(TimeUnit.class));
        assertThat(keyCap.getValue()).isEqualTo("post_view:7:u:42");
    }

    /**
     * 验证属性 4-C：未登录且 IP 解析失败（null/空白）时退化为 "unknown" 桶，
     * 仍然能阻挡同源刷数；但运维需要监控该桶规模，长期非空即为代理配置异常。
     */
    @Test
    @DisplayName("userId=null 且 ip 为空白时退化到 unknown 桶")
    void userIdNull_ipBlank_usesUnknownKey() {
        when(valueOps.setIfAbsent(anyString(), eq("1"), anyLong(), any(TimeUnit.class)))
                .thenReturn(Boolean.TRUE);

        deduper.shouldCount(9L, null, "  ");

        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        verify(valueOps).setIfAbsent(keyCap.capture(), eq("1"), anyLong(), any(TimeUnit.class));
        assertThat(keyCap.getValue()).isEqualTo("post_view:9:ip:unknown");
    }
}
