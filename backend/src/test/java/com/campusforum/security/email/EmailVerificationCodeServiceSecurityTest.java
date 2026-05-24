package com.campusforum.security.email;

import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.infra.email.EmailCodeScene;
import com.campusforum.infra.email.EmailProperties;
import com.campusforum.infra.email.EmailService;
import com.campusforum.infra.security.TrustedProxyResolver;
import com.campusforum.tenant.TenantContext;
import com.campusforum.user.mapper.UserMapper;
import com.campusforum.user.service.EmailVerificationCodeService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link EmailVerificationCodeService} 安全性单元测试（任务 T5.4）。
 *
 * <p>对应 bugfix.md 漏洞 11（{@code isRateLimited} fail-open + {@code String.equals} 时序泄漏）
 * 与漏洞 16（IP 维度限流缺失）的修复验证：</p>
 * <ul>
 *   <li><b>fail-closed</b>：Redis 不可用时 {@code isRateLimited} 不再静默放行，应抛
 *       {@link ErrorCode#SERVICE_UNAVAILABLE}；</li>
 *   <li><b>常量时间比较</b>：{@code verifyAndConsume} 用 {@code MessageDigest.isEqual}
 *       替代 {@code String.equals}，等长 / 不等长 / 内容不同的错误路径都返回相同的
 *       "无效或已过期" 错误；</li>
 *   <li><b>IP 维度计数</b>：单 IP 每分钟最多 3 次请求，第 4 次起返回
 *       {@link ErrorCode#RATE_LIMITED}；该计数器自身的 Redis 异常也走 fail-closed。</li>
 * </ul>
 *
 * <p>测试一律使用 Mockito mock 协作组件，<strong>不启动 Spring 上下文 / 不连接真实
 * Redis / MySQL</strong>，符合 "纯单元测试" 约束，可在任何机器上快速运行。</p>
 */
class EmailVerificationCodeServiceSecurityTest {

    /** 被测对象。 */
    private EmailVerificationCodeService service;

    /** Mock 协作者。 */
    private StringRedisTemplate redisTemplate;
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private EmailService emailService;
    private EmailProperties emailProperties;
    private UserMapper userMapper;
    private TrustedProxyResolver trustedProxyResolver;
    private HttpServletRequest httpRequest;

    /** 测试常量：固定租户 ID + 邮箱 + IP。 */
    private static final long TENANT_ID = 1L;
    private static final String EMAIL = "alice@example.com";
    private static final String CLIENT_IP = "203.0.113.5";

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        emailService = mock(EmailService.class);
        emailProperties = new EmailProperties();
        userMapper = mock(UserMapper.class);
        trustedProxyResolver = mock(TrustedProxyResolver.class);
        httpRequest = mock(HttpServletRequest.class);

        // 默认 ValueOperations 路由（每个用例可按需 stub get / increment）
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        // sendCode 入口会调用 trustedProxyResolver.resolve(httpRequest) 取 IP；
        // 用 lenient() 让 "verifyAndConsume 单点用例" 不要因未使用 stub 报 strict mode 错
        lenient().when(trustedProxyResolver.resolve(httpRequest)).thenReturn(CLIENT_IP);

        service = new EmailVerificationCodeService(
                redisTemplate,
                emailService,
                emailProperties,
                userMapper,
                trustedProxyResolver,
                httpRequest);

        // sendCode 内部 requireTenantId() 必须能拿到非空 tenantId
        TenantContext.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------
    //   fail-closed：Redis 异常时 isRateLimited 抛 SERVICE_UNAVAILABLE
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Redis 不可用时 sendCode 应 fail-closed 抛 SERVICE_UNAVAILABLE（漏洞 11）")
    void redisDown_isRateLimited_throws503() {
        // 注册场景，邮箱未占用 -> 进入 IP 维度计数 -> 通过；进入邮箱维度限流 -> Redis 抛连接异常
        when(userMapper.selectCount(any())).thenReturn(0L);

        // IP 维度计数：第 1 次 -> 1（通过）；TTL 设置 stub
        when(valueOps.increment(anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        // 邮箱维度限流读取计数时 Redis 抛错
        when(valueOps.get(anyString())).thenThrow(
                new RedisConnectionFailureException("simulated redis down"));

        assertThatThrownBy(() -> service.sendCode(EMAIL, EmailCodeScene.REGISTER))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE.getCode());

        // 关键反向断言：fail-closed 后绝不再调用邮件下发链路
        verify(emailService, never()).sendVerificationCode(anyString(), any(), anyString(), anyInt());
    }

    // ------------------------------------------------------------------
    //   verifyAndConsume：常量时间比较替代 String.equals
    // ------------------------------------------------------------------

    @Test
    @DisplayName("verifyAndConsume：等值验证码通过且删除 Redis key（漏洞 11 - 正向流程）")
    void verifyAndConsume_constantTimeCompare_works() {
        when(valueOps.get(anyString())).thenReturn("123456");

        assertThatCode(() -> service.verifyAndConsume(
                TENANT_ID, EMAIL, EmailCodeScene.LOGIN, "123456"))
                .doesNotThrowAnyException();

        // 消费成功后 Redis key 必须被删除，避免重放
        verify(redisTemplate).delete(anyString());
    }

    @Test
    @DisplayName("verifyAndConsume：等长但内容不同时抛 BAD_REQUEST（漏洞 11 - 反向 1）")
    void verifyAndConsume_wrongCode_throws() {
        when(valueOps.get(anyString())).thenReturn("123456");

        assertThatThrownBy(() -> service.verifyAndConsume(
                TENANT_ID, EMAIL, EmailCodeScene.LOGIN, "999999"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.BAD_REQUEST.getCode());

        // 失败路径不能误删 Redis key
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("verifyAndConsume：长度不一致时抛 BAD_REQUEST（漏洞 11 - 反向 2，常量时间比较的关键场景）")
    void verifyAndConsume_lengthMismatch_throws() {
        when(valueOps.get(anyString())).thenReturn("123456");

        // 用 5 位输入对比 6 位存储：String.equals 会立即返回 false（短路），
        // MessageDigest.isEqual 会比较所有字节且耗时与 "等长但内容不同" 一致
        assertThatThrownBy(() -> service.verifyAndConsume(
                TENANT_ID, EMAIL, EmailCodeScene.LOGIN, "12345"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.BAD_REQUEST.getCode());

        verify(redisTemplate, never()).delete(anyString());
    }

    // ------------------------------------------------------------------
    //   IP 维度计数：第 4 次起 RATE_LIMITED；Redis 异常 fail-closed
    // ------------------------------------------------------------------

    @Test
    @DisplayName("同一 IP 第 4 次 sendCode 应被 RATE_LIMITED 拒绝（漏洞 16）")
    void ipRate_4thRequest_isRejected() {
        // 注册场景，邮箱未占用 -> 进入 IP 维度计数
        when(userMapper.selectCount(any())).thenReturn(0L);

        // IP 维度计数：第 4 次 INCR 返回 4（> 3）即应触发限流
        when(valueOps.increment(anyString())).thenReturn(4L);

        assertThatThrownBy(() -> service.sendCode(EMAIL, EmailCodeScene.REGISTER))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.RATE_LIMITED.getCode());

        // IP 限流命中后绝不应再触发邮件下发
        verify(emailService, never()).sendVerificationCode(anyString(), any(), anyString(), anyInt());
    }

    @Test
    @DisplayName("IP 维度限流 Redis 故障时也 fail-closed 抛 SERVICE_UNAVAILABLE（漏洞 16）")
    void ipRate_redisDown_failClosed() {
        when(userMapper.selectCount(any())).thenReturn(0L);

        // INCR 阶段直接抛 Redis 连接异常 -> checkAndIncrementIpRate 应 fail-closed
        when(valueOps.increment(anyString())).thenThrow(
                new RedisConnectionFailureException("simulated redis down"));

        assertThatThrownBy(() -> service.sendCode(EMAIL, EmailCodeScene.REGISTER))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.SERVICE_UNAVAILABLE.getCode());

        verify(emailService, never()).sendVerificationCode(anyString(), any(), anyString(), anyInt());
    }

    // ------------------------------------------------------------------
    //   边界用例：邮箱不存在的非注册场景静默 return，不应触发 IP 计数
    //   （避免攻击者借不存在邮箱反制正常用户的 IP 限流）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("登录场景下不存在的邮箱静默 return：不触发 IP 计数 / 不发邮件")
    void nonExistentEmail_silentReturn_doesNotIncrementIpRate() {
        // findEnabledUser 返回 null，说明邮箱不存在 / 未启用
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThatCode(() -> service.sendCode(EMAIL, EmailCodeScene.LOGIN))
                .doesNotThrowAnyException();

        // IP 计数 / 邮件下发都不应触发
        verify(valueOps, never()).increment(anyString());
        verify(emailService, never()).sendVerificationCode(anyString(), any(), anyString(), anyInt());
    }
}
