package com.campusforum.security.ws;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.infra.metrics.SecurityMetrics;
import com.campusforum.infra.security.SecurityProperties;
import com.campusforum.infra.security.WsTicketService;
import com.campusforum.tenant.websocket.TenantHandshakeInterceptor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * 单元测试：TenantHandshakeInterceptor —— T3.4 加固验证。
 *
 * <p>本类专门覆盖 T3.4 中新增的两条加固行为：</p>
 * <ul>
 *   <li><b>漏洞 29</b>：{@code extractQueryParam} 改用 {@code getRawQuery()} +
 *       {@code URLDecoder.decode(UTF_8)}，确保 ticket 中 base64 控制字符
 *       （{@code +} / {@code /} / {@code =}）经 {@code encodeURIComponent}
 *       编码后能被还原成签发时的原文。</li>
 *   <li><b>漏洞 8</b>：{@code verifyByLegacyToken} 入口每次都累加
 *       {@link SecurityMetrics#wsLegacyTokenUsed()} Counter，并按
 *       60 秒节流输出 WARN 日志（含来源 IP / User-Agent），便于在
 *       cutover 前度量迁移完成度并定位仍在使用 legacy 的客户端。</li>
 * </ul>
 *
 * <p>相比 {@code com.campusforum.tenant.websocket.TenantHandshakeInterceptorTest}
 * 已有的"行为契约"测试，本类专注 T3.4 新增的边角行为，使用真实
 * {@link SimpleMeterRegistry} + 真实 {@link SecurityMetrics} 而非 mock，
 * 同时通过 Logback {@link ListAppender} 捕获 WARN 日志做断言。</p>
 */
class TenantHandshakeInterceptorTest {

    private TenantHandshakeInterceptor interceptor;
    private WsTicketService wsTicketService;
    private SecurityProperties securityProperties;
    private MeterRegistry meterRegistry;
    private SecurityMetrics securityMetrics;

    private ServerHttpRequest request;
    private ServerHttpResponse response;
    private WebSocketHandler wsHandler;
    private Map<String, Object> attributes;
    private MockedStatic<StpUtil> stpUtilMock;

    /** 捕获 TenantHandshakeInterceptor 的日志输出，用于 WARN 限频断言。 */
    private ListAppender<ILoggingEvent> logAppender;
    private Logger interceptorLogger;

    @BeforeEach
    void setUp() {
        wsTicketService = mock(WsTicketService.class);
        securityProperties = new SecurityProperties();
        // 默认未强制 ticket，允许走 legacy 分支以便测试 WARN/Counter 行为
        securityProperties.getWsTicket().setEnforced(false);
        meterRegistry = new SimpleMeterRegistry();
        securityMetrics = new SecurityMetrics(meterRegistry);
        interceptor = new TenantHandshakeInterceptor(wsTicketService, securityProperties, securityMetrics);

        request = mock(ServerHttpRequest.class);
        response = mock(ServerHttpResponse.class);
        wsHandler = mock(WebSocketHandler.class);
        attributes = new HashMap<>();
        stpUtilMock = mockStatic(StpUtil.class);

        // 绑定 Logback ListAppender 到 TenantHandshakeInterceptor 的 Logger
        interceptorLogger = (Logger) LoggerFactory.getLogger(TenantHandshakeInterceptor.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        interceptorLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        if (logAppender != null) {
            interceptorLogger.detachAppender(logAppender);
            logAppender.stop();
        }
        stpUtilMock.close();
    }

    // ===================================================================
    // 漏洞 29：query 参数 URL decode
    // ===================================================================

    @Test
    @DisplayName("漏洞 29：ticket 含 %2B/%2F/%3D 经 URL decode 后应传给 wsTicketService.verify")
    void ticket_with_url_encoded_chars_is_parsed() throws Exception {
        // 模拟前端 encodeURIComponent("abc+def/ghi=") → "abc%2Bdef%2Fghi%3D"
        // 使用单参 URI 构造，它把传入字符串视为"已经百分号编码"，
        // 不再二次重写，从而 getRawQuery() 能拿到与生产请求一致的原始 query
        URI uri = new URI("ws://localhost/ws/notify?ticket=abc%2Bdef%2Fghi%3D");
        when(request.getURI()).thenReturn(uri);
        when(wsTicketService.verify("abc+def/ghi=")).thenReturn(
                new WsTicketService.Verified(42L, 5L));

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        // 校验解码后字符串被精确传给 verify（含 + / = 三个 base64 控制字符）
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(wsTicketService).verify(captor.capture());
        assertThat(captor.getValue()).isEqualTo("abc+def/ghi=");
        assertThat(result).isTrue();
        assertThat(attributes.get("userId")).isEqualTo(42L);
        assertThat(attributes.get("tenantId")).isEqualTo(5L);
    }

    @Test
    @DisplayName("漏洞 29：raw query 中 %2B 必须解码成 + 字面量传给 verify")
    void ticket_with_literal_plus_is_kept_as_plus() throws Exception {
        // 单参构造，把 query 当作"已编码原文"使用
        URI uri = new URI("ws://localhost/ws/notify?ticket=abc%2B123");
        when(request.getURI()).thenReturn(uri);
        when(wsTicketService.verify("abc+123")).thenReturn(
                new WsTicketService.Verified(7L, 1L));

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isTrue();
        org.mockito.Mockito.verify(wsTicketService).verify("abc+123");
    }

    // ===================================================================
    // 漏洞 8：legacy token 路径埋点 + 限频 WARN
    // ===================================================================

    @Test
    @DisplayName("漏洞 8：legacy 分支每次都累加 ws_legacy_token_used Counter")
    void legacy_branch_increments_counter_each_time() throws Exception {
        // 构造 query 含 token 但无 ticket，进入 legacy 分支；
        // token 校验失败也不影响 Counter 累加（只要进入了 verifyByLegacyToken 即计数）
        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/notify?token=t1"));
        when(request.getHeaders()).thenReturn(new HttpHeaders());
        stpUtilMock.when(() -> StpUtil.getLoginIdByToken("t1"))
                .thenThrow(new RuntimeException("token invalid"));

        for (int i = 0; i < 3; i++) {
            interceptor.beforeHandshake(request, response, wsHandler, new HashMap<>());
        }

        Counter counter = meterRegistry.find("ws_legacy_token_used").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("漏洞 8：legacy 分支 WARN 日志按 60s 节流（同窗口内多次调用只输出 1 条）")
    void legacy_branch_warn_log_isThrottled() throws Exception {
        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/notify?token=t1"));
        when(request.getHeaders()).thenReturn(new HttpHeaders());
        stpUtilMock.when(() -> StpUtil.getLoginIdByToken("t1"))
                .thenThrow(new RuntimeException("token invalid"));

        // 在毫秒级时间窗口内连续调 5 次，第一次输出 WARN，其余 4 次仅累加 Counter
        for (int i = 0; i < 5; i++) {
            interceptor.beforeHandshake(request, response, wsHandler, new HashMap<>());
        }

        long warnCount = logAppender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .filter(e -> e.getFormattedMessage().contains("WS legacy token 路径被使用"))
                .count();
        assertThat(warnCount).isEqualTo(1L);

        // Counter 应等于实际进入次数（5），与 WARN 限频解耦
        Counter counter = meterRegistry.find("ws_legacy_token_used").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("漏洞 8：WARN 日志包含来源 IP 与 User-Agent")
    void legacy_branch_records_ip_and_ua_in_warn() throws Exception {
        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/notify?token=t1"));
        when(request.getRemoteAddress()).thenReturn(new InetSocketAddress("1.2.3.4", 54321));
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (TestAgent)");
        when(request.getHeaders()).thenReturn(headers);
        stpUtilMock.when(() -> StpUtil.getLoginIdByToken("t1"))
                .thenThrow(new RuntimeException("token invalid"));

        interceptor.beforeHandshake(request, response, wsHandler, new HashMap<>());

        ILoggingEvent warn = logAppender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .filter(e -> e.getFormattedMessage().contains("WS legacy token 路径被使用"))
                .findFirst()
                .orElseThrow();
        assertThat(warn.getFormattedMessage()).contains("1.2.3.4");
        assertThat(warn.getFormattedMessage()).contains("Mozilla/5.0 (TestAgent)");
    }

    @Test
    @DisplayName("漏洞 8：ticket 路径不应触发 ws_legacy_token_used Counter")
    void ticket_branch_does_not_increment_legacy_counter() throws Exception {
        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/notify?ticket=abc"));
        when(wsTicketService.verify("abc"))
                .thenReturn(new WsTicketService.Verified(1L, 1L));

        interceptor.beforeHandshake(request, response, wsHandler, attributes);

        Counter counter = meterRegistry.find("ws_legacy_token_used").counter();
        // 该 Counter 可能尚未注册（从未调用过），也可能注册但计数为 0
        if (counter != null) {
            assertThat(counter.count()).isEqualTo(0.0);
        }
    }

    @Test
    @DisplayName("漏洞 8：enforced=true 时不进入 legacy 分支，Counter 不累加")
    void enforced_mode_does_not_increment_legacy_counter() throws Exception {
        securityProperties.getWsTicket().setEnforced(true);
        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/notify?token=t1"));

        interceptor.beforeHandshake(request, response, wsHandler, attributes);

        Counter counter = meterRegistry.find("ws_legacy_token_used").counter();
        if (counter != null) {
            assertThat(counter.count()).isEqualTo(0.0);
        }
    }

    @Test
    @DisplayName("legacy 分支：token 校验通过应正确写入 attributes 且 Counter 累加 1")
    void legacy_branch_happy_path_still_works_and_increments_counter() throws Exception {
        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/notify?token=goodtoken"));
        when(request.getHeaders()).thenReturn(new HttpHeaders());

        stpUtilMock.when(() -> StpUtil.getLoginIdByToken("goodtoken")).thenReturn(99L);
        SaSession session = mock(SaSession.class);
        stpUtilMock.when(() -> StpUtil.getSessionByLoginId(99L)).thenReturn(session);
        when(session.get("tenantId")).thenReturn(2L);

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isTrue();
        assertThat(attributes.get("userId")).isEqualTo(99L);
        assertThat(attributes.get("tenantId")).isEqualTo(2L);

        Counter counter = meterRegistry.find("ws_legacy_token_used").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }
}
