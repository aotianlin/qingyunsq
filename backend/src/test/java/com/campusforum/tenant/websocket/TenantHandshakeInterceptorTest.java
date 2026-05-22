package com.campusforum.tenant.websocket;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.infra.security.SecurityProperties;
import com.campusforum.infra.security.WsTicketService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * 单元测试：TenantHandshakeInterceptor
 *
 * <p>验证 WebSocket 握手阶段的 ticket / token 提取、验证和 tenantId 写入逻辑。</p>
 *
 * <p>安全加固后行为：</p>
 * <ul>
 *   <li>优先识别 {@code ?ticket=} 走 {@link WsTicketService#verify(String)}；</li>
 *   <li>未提供 ticket 时回退到旧 {@code ?token=} / Authorization header（兼容期）；</li>
 *   <li>{@code security.ws-ticket.enforced=true} 时禁用旧 token 路径，强制 ticket。</li>
 * </ul>
 */
class TenantHandshakeInterceptorTest {

    private TenantHandshakeInterceptor interceptor;
    private WsTicketService wsTicketService;
    private SecurityProperties securityProperties;
    private ServerHttpRequest request;
    private ServerHttpResponse response;
    private WebSocketHandler wsHandler;
    private Map<String, Object> attributes;
    private MockedStatic<StpUtil> stpUtilMock;

    private static final long USER_ID = 42L;
    private static final long TENANT_ID = 5L;
    private static final String VALID_TOKEN = "abc123token";
    private static final String VALID_TICKET = "header.signature";

    @BeforeEach
    void setUp() {
        wsTicketService = mock(WsTicketService.class);
        securityProperties = new SecurityProperties();
        // 默认未强制 ticket，允许兼容旧 token 路径
        securityProperties.getWsTicket().setEnforced(false);
        interceptor = new TenantHandshakeInterceptor(wsTicketService, securityProperties);
        request = mock(ServerHttpRequest.class);
        response = mock(ServerHttpResponse.class);
        wsHandler = mock(WebSocketHandler.class);
        attributes = new HashMap<>();
        stpUtilMock = mockStatic(StpUtil.class);
    }

    @AfterEach
    void tearDown() {
        stpUtilMock.close();
    }

    // ===== ticket 路径（推荐）=====

    @Test
    @DisplayName("合法 ticket → 握手成功，attributes 含 userId/tenantId")
    void shouldAcceptValidTicket() throws Exception {
        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/notify?ticket=" + VALID_TICKET));
        when(wsTicketService.verify(VALID_TICKET))
                .thenReturn(new WsTicketService.Verified(USER_ID, TENANT_ID));

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isTrue();
        assertThat(attributes.get("userId")).isEqualTo(USER_ID);
        assertThat(attributes.get("tenantId")).isEqualTo(TENANT_ID);
        verify(response, never()).setStatusCode(any());
        // ticket 路径不应再触发 Sa-Token 主令牌校验
        stpUtilMock.verifyNoInteractions();
    }

    @Test
    @DisplayName("非法 ticket → 401 拒绝握手")
    void shouldRejectInvalidTicket() throws Exception {
        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/notify?ticket=bad"));
        when(wsTicketService.verify("bad")).thenReturn(null);

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        assertThat(attributes).isEmpty();
    }

    @Test
    @DisplayName("ticket.enforced=true 时拒绝旧 token 路径")
    void shouldRejectLegacyTokenWhenTicketEnforced() throws Exception {
        securityProperties.getWsTicket().setEnforced(true);
        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/notify?token=" + VALID_TOKEN));
        when(request.getHeaders()).thenReturn(new HttpHeaders());

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        // enforced 模式下不应进入旧 token 校验链路
        stpUtilMock.verifyNoInteractions();
    }

    // ===== 旧 token 兼容路径（过渡期）=====

    @Test
    @DisplayName("兼容期：合法 token 从 query param → 握手成功")
    void shouldAcceptValidTokenFromQueryParam() throws Exception {
        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/notify?token=" + VALID_TOKEN));
        when(request.getHeaders()).thenReturn(new HttpHeaders());

        stpUtilMock.when(() -> StpUtil.getLoginIdByToken(VALID_TOKEN)).thenReturn(USER_ID);
        SaSession session = mock(SaSession.class);
        stpUtilMock.when(() -> StpUtil.getSessionByLoginId(USER_ID)).thenReturn(session);
        when(session.get("tenantId")).thenReturn(TENANT_ID);

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isTrue();
        assertThat(attributes.get("userId")).isEqualTo(USER_ID);
        assertThat(attributes.get("tenantId")).isEqualTo(TENANT_ID);
        verify(response, never()).setStatusCode(any());
    }

    @Test
    @DisplayName("兼容期：合法 token 从 Authorization header → 握手成功")
    void shouldAcceptValidTokenFromAuthorizationHeader() throws Exception {
        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/notify"));
        HttpHeaders headers = new HttpHeaders();
        headers.put("Authorization", List.of(VALID_TOKEN));
        when(request.getHeaders()).thenReturn(headers);

        stpUtilMock.when(() -> StpUtil.getLoginIdByToken(VALID_TOKEN)).thenReturn(USER_ID);
        SaSession session = mock(SaSession.class);
        stpUtilMock.when(() -> StpUtil.getSessionByLoginId(USER_ID)).thenReturn(session);
        when(session.get("tenantId")).thenReturn(TENANT_ID);

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isTrue();
        assertThat(attributes.get("userId")).isEqualTo(USER_ID);
        assertThat(attributes.get("tenantId")).isEqualTo(TENANT_ID);
    }

    @Test
    @DisplayName("无 ticket 也无 token → 401 拒绝握手")
    void shouldRejectWhenNoCredential() throws Exception {
        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/notify"));
        when(request.getHeaders()).thenReturn(new HttpHeaders());

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        assertThat(attributes).isEmpty();
    }

    @Test
    @DisplayName("兼容期：无效 token（getLoginIdByToken 抛异常）→ 401")
    void shouldRejectWhenTokenIsInvalid() throws Exception {
        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/notify?token=invalid-token"));
        when(request.getHeaders()).thenReturn(new HttpHeaders());

        stpUtilMock.when(() -> StpUtil.getLoginIdByToken("invalid-token"))
                .thenThrow(new RuntimeException("token invalid"));

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("兼容期：token 有效但 getLoginIdByToken 返回 null → 401")
    void shouldRejectWhenLoginIdIsNull() throws Exception {
        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/notify?token=" + VALID_TOKEN));
        when(request.getHeaders()).thenReturn(new HttpHeaders());

        stpUtilMock.when(() -> StpUtil.getLoginIdByToken(VALID_TOKEN)).thenReturn(null);

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("兼容期：loginId 非数字 → 401")
    void shouldRejectWhenLoginIdIsNotNumeric() throws Exception {
        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/notify?token=" + VALID_TOKEN));
        when(request.getHeaders()).thenReturn(new HttpHeaders());

        stpUtilMock.when(() -> StpUtil.getLoginIdByToken(VALID_TOKEN)).thenReturn("not-a-number");

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("兼容期：session 缺 tenantId → 401")
    void shouldRejectWhenSessionMissingTenantId() throws Exception {
        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/notify?token=" + VALID_TOKEN));
        when(request.getHeaders()).thenReturn(new HttpHeaders());

        stpUtilMock.when(() -> StpUtil.getLoginIdByToken(VALID_TOKEN)).thenReturn(USER_ID);
        SaSession session = mock(SaSession.class);
        stpUtilMock.when(() -> StpUtil.getSessionByLoginId(USER_ID)).thenReturn(session);
        when(session.get("tenantId")).thenReturn(null);

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("query param token 为空 → 401")
    void shouldRejectWhenTokenParamIsEmpty() throws Exception {
        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/notify?token="));
        when(request.getHeaders()).thenReturn(new HttpHeaders());

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("query string 多参数中提取 token")
    void shouldExtractTokenFromMiddleOfQueryString() throws Exception {
        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/notify?foo=bar&token=" + VALID_TOKEN + "&baz=qux"));
        when(request.getHeaders()).thenReturn(new HttpHeaders());

        stpUtilMock.when(() -> StpUtil.getLoginIdByToken(VALID_TOKEN)).thenReturn(USER_ID);
        SaSession session = mock(SaSession.class);
        stpUtilMock.when(() -> StpUtil.getSessionByLoginId(USER_ID)).thenReturn(session);
        when(session.get("tenantId")).thenReturn(TENANT_ID);

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isTrue();
        assertThat(attributes.get("tenantId")).isEqualTo(TENANT_ID);
    }

    @Test
    @DisplayName("Integer 类型 tenantId 正确转 long")
    void shouldHandleIntegerTenantId() throws Exception {
        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/notify?token=" + VALID_TOKEN));
        when(request.getHeaders()).thenReturn(new HttpHeaders());

        stpUtilMock.when(() -> StpUtil.getLoginIdByToken(VALID_TOKEN)).thenReturn(USER_ID);
        SaSession session = mock(SaSession.class);
        stpUtilMock.when(() -> StpUtil.getSessionByLoginId(USER_ID)).thenReturn(session);
        when(session.get("tenantId")).thenReturn(Integer.valueOf(3));

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isTrue();
        assertThat(attributes.get("tenantId")).isEqualTo(3L);
    }
}
