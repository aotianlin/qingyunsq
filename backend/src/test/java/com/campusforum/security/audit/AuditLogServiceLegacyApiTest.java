package com.campusforum.security.audit;

import com.campusforum.admin.domain.AuditLog;
import com.campusforum.admin.mapper.AuditLogMapper;
import com.campusforum.infra.audit.AuditLogService;
import com.campusforum.infra.security.TrustedProxyResolver;
import com.campusforum.user.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 旧 4 参 {@link AuditLogService#log(String, String, Long, String)} 兼容性测试
 * （preservation-checking）。
 *
 * <p>对应任务 T9.2：在重构 5 参 API 的同时，旧 4 参方法必须保留并继续工作，
 * 以便后续任务（T3.1 / T9.4 等）按节奏迁移调用方而不是一次性大爆破。</p>
 *
 * <p>本测试覆盖两条兜底路径：</p>
 * <ol>
 *   <li>Servlet 线程下：从 {@link RequestContextHolder} 拿 request，IP/UA 正常写入；</li>
 *   <li>异步线程下：从 {@link MDC} 兜底拼装上下文，至少把 operatorId / clientIp 保留。</li>
 * </ol>
 */
class AuditLogServiceLegacyApiTest {

    private AuditLogMapper auditLogMapper;
    private UserMapper userMapper;
    private HttpServletRequest injectedRequest;
    private TrustedProxyResolver trustedProxyResolver;
    private AuditLogService service;

    @BeforeEach
    void setUp() {
        auditLogMapper = mock(AuditLogMapper.class);
        userMapper = mock(UserMapper.class);
        // 模拟 Spring 注入的 request-scope 代理：异步分支下解引用会抛异常，
        // 但这里我们走 Servlet 分支，injectedRequest 实际不会被使用。
        injectedRequest = mock(HttpServletRequest.class);
        trustedProxyResolver = mock(TrustedProxyResolver.class);
        service = new AuditLogService(auditLogMapper, userMapper, injectedRequest, trustedProxyResolver);
    }

    @AfterEach
    void tearDown() {
        // 确保测试间不污染 RequestContextHolder / MDC
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    @Test
    @DisplayName("Servlet 线程：旧 4 参调用通过 RequestContextHolder 兜底，IP/UA 正常写入")
    void deprecated_4arg_call_still_works() {
        // 1) mock 一个真实的 Servlet 请求并塞进 RequestContextHolder（模拟 controller 调用入口）
        MockHttpServletRequest mockReq = new MockHttpServletRequest();
        mockReq.setRemoteAddr("198.51.100.5");
        mockReq.addHeader("User-Agent", "Mozilla/5.0 LegacyClient");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockReq));

        // 2) TrustedProxyResolver 返回真实 IP（模拟可信代理白名单解析后的结果）
        when(trustedProxyResolver.resolve(any(HttpServletRequest.class))).thenReturn("198.51.100.5");

        // 3) 调用旧 4 参 API（注意：调用方未传 ctx）
        service.log("USER_ROLE_CHANGE", "user", 7L, "promoted to admin");

        // 4) 断言写库参数正确，IP / UA 都被兜底解析出来
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insert(captor.capture());
        AuditLog written = captor.getValue();

        assertThat(written.getAction()).isEqualTo("USER_ROLE_CHANGE");
        assertThat(written.getTargetType()).isEqualTo("user");
        assertThat(written.getTargetId()).isEqualTo(7L);
        // 任务 T9.2 兼容性：detail 列是 JSON，AuditLogService 会把纯文本 detail 包成
        // JSON 字符串字面量后再写库；旧 4 参调用方传入的 "promoted to admin" 现在落库为
        // 双引号包裹形式，便于 MySQL 8 严格模式接受。
        assertThat(written.getDetail()).isEqualTo("\"promoted to admin\"");
        assertThat(written.getIpAddress()).isEqualTo("198.51.100.5");
        assertThat(written.getUserAgent()).isEqualTo("Mozilla/5.0 LegacyClient");
    }

    @Test
    @DisplayName("异步线程（无 RequestContextHolder）：旧 4 参从 MDC 兜底，仍能写入审计")
    void deprecated_4arg_fallsBackToMdc_whenNoRequestBound() {
        // 1) 故意不设置 RequestContextHolder，并用 MDC 装入异步线程上下文
        MDC.put("userId", "99");
        MDC.put("tenantId", "3");
        MDC.put("clientIp", "10.0.0.42");

        // 2) 旧 4 参调用：内部 currentRequestContext() 拿不到 request，必须退到 MDC
        service.log("ASYNC_TASK", "system", null, "background reindex");

        // 3) 断言 mapper 仍然收到了写入请求，operator / IP 来自 MDC
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insert(captor.capture());
        AuditLog written = captor.getValue();

        assertThat(written.getAction()).isEqualTo("ASYNC_TASK");
        assertThat(written.getOperatorId()).isEqualTo(99L);
        assertThat(written.getIpAddress()).isEqualTo("10.0.0.42");
        // UA 在异步分支下确实拿不到，应为 null
        assertThat(written.getUserAgent()).isNull();
    }

    @Test
    @DisplayName("异步线程 + MDC 全空：旧 4 参仍不抛异常，仅写入 action / detail")
    void deprecated_4arg_emptyMdc_doesNotThrow() {
        // 完全没有上下文（既没 RequestContextHolder 也没 MDC），写库的"骨架"必须保留
        service.log("ANONYMOUS_EVENT", null, null, null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogMapper).insert(captor.capture());
        AuditLog written = captor.getValue();

        assertThat(written.getAction()).isEqualTo("ANONYMOUS_EVENT");
        assertThat(written.getOperatorId()).isNull();
        assertThat(written.getIpAddress()).isNull();
    }
}
