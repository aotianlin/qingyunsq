package com.campusforum.security.audit;

import com.campusforum.admin.domain.AuditLog;
import com.campusforum.admin.mapper.AuditLogMapper;
import com.campusforum.infra.audit.AuditContext;
import com.campusforum.infra.audit.AuditLogService;
import com.campusforum.infra.security.TrustedProxyResolver;
import com.campusforum.user.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AuditLogService 5 参 API 在异步线程下的行为验证（fix-checking）。
 *
 * <p>对应任务 T9.2 / bugfix.md 漏洞 26：原 AuditLogService 依赖 request-scope
 * HttpServletRequest 代理，在 {@link CompletableFuture#runAsync} 等异步线程中
 * 调用会抛 {@link IllegalStateException}。重构后，调用方可在 Servlet 线程内
 * 显式构造 {@link AuditContext} 快照，再传到任意后台线程，写库不再依赖
 * ThreadLocal。</p>
 */
class AuditContextAsyncTest {

    private AuditLogMapper auditLogMapper;
    private UserMapper userMapper;
    private HttpServletRequest request;
    private TrustedProxyResolver trustedProxyResolver;
    private AuditLogService service;

    @BeforeEach
    void setUp() {
        auditLogMapper = mock(AuditLogMapper.class);
        userMapper = mock(UserMapper.class);
        // 关键：把 request 配置成"任意方法都抛 IllegalStateException"，
        // 模拟异步线程下 request-scope 代理失效的真实情况。
        request = mock(HttpServletRequest.class, invocation -> {
            throw new IllegalStateException(
                    "No thread-bound request found: simulate async thread");
        });
        trustedProxyResolver = mock(TrustedProxyResolver.class);
        service = new AuditLogService(auditLogMapper, userMapper, request, trustedProxyResolver);
    }

    @Test
    @DisplayName("异步线程内显式构造 AuditContext → 5 参 log() 写入成功")
    void asyncThread_can_write_audit_without_request() throws ExecutionException, InterruptedException {
        // 1) 假设调用方在 Servlet 线程预先构造一份快照（这里直接 builder 模拟）
        AuditContext ctx = AuditContext.builder()
                .operatorId(42L)
                .tenantId(7L)
                .clientIp("203.0.113.9")
                .userAgent("Mozilla/5.0 (test)")
                .build();

        // 2) 在 ForkJoinPool 公共池上跑异步任务，验证不依赖 ThreadLocal
        CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
            service.log(ctx, "PASSWORD_RESET", "user", 42L, "reset by email code");
        });
        task.get();

        // 3) 断言：mapper 收到了完整字段（IP / UA / operator）
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper, times(1)).insert(captor.capture());
        AuditLog written = captor.getValue();

        assertThat(written.getOperatorId()).isEqualTo(42L);
        assertThat(written.getAction()).isEqualTo("PASSWORD_RESET");
        assertThat(written.getTargetType()).isEqualTo("user");
        assertThat(written.getTargetId()).isEqualTo(42L);
        // 任务 T9.2：detail 列是 JSON，纯文本会被包成 JSON 字符串字面量后再写库
        assertThat(written.getDetail()).isEqualTo("\"reset by email code\"");
        assertThat(written.getIpAddress()).isEqualTo("203.0.113.9");
        assertThat(written.getUserAgent()).isEqualTo("Mozilla/5.0 (test)");

        // 4) 关键反向断言：异步线程内绝不会触碰 request 代理
        verifyNoInteractions(trustedProxyResolver);
    }

    @Test
    @DisplayName("超长 User-Agent → 服务层主动截断到 255 字符，避免 SQL 截断告警")
    void asyncThread_truncatesOverlengthUserAgent() {
        String longUa = "A".repeat(500);
        AuditContext ctx = AuditContext.builder()
                .operatorId(1L)
                .userAgent(longUa)
                .build();

        service.log(ctx, "TEST", "obj", 1L, "detail");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserAgent()).hasSize(255);
    }

    @Test
    @DisplayName("空 AuditContext → 仍写入审计但 IP/UA 字段为 null，不抛异常")
    void asyncThread_nullContext_doesNotThrow() {
        // null ctx 是兜底分支：调用方什么都没传也要写一条审计
        service.log((AuditContext) null, "SYSTEM_TASK", "system", null, "scheduled job");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insert(captor.capture());
        AuditLog written = captor.getValue();
        assertThat(written.getAction()).isEqualTo("SYSTEM_TASK");
        assertThat(written.getOperatorId()).isNull();
        assertThat(written.getIpAddress()).isNull();
        assertThat(written.getUserAgent()).isNull();
    }

    @Test
    @DisplayName("AuditContext.from(req,resolver,...) 在异步线程下不抛异常，IP/UA 字段为空")
    void from_isSafeUnderAsyncRequestProxy() {
        // 即便传入"被代理化"的 request，工厂也不应该把异常向上抛
        AuditContext ctx = AuditContext.from(request, trustedProxyResolver, 11L, 7L);
        assertThat(ctx.getOperatorId()).isEqualTo(11L);
        assertThat(ctx.getTenantId()).isEqualTo(7L);
        assertThat(ctx.getClientIp()).isNull();
        assertThat(ctx.getUserAgent()).isNull();
    }
}
