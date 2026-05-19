package com.campusforum.tenant.websocket;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
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
 * <p>验证 WebSocket 握手阶段的 token 提取、验证和 tenantId 写入逻辑。</p>
 *
 * <p><b>Validates: AC-5.1, AC-5.2</b></p>
 */
class TenantHandshakeInterceptorTest {

    private TenantHandshakeInterceptor interceptor;
    private ServerHttpRequest request;
    private ServerHttpResponse response;
    private WebSocketHandler wsHandler;
    private Map<String, Object> attributes;
    private MockedStatic<StpUtil> stpUtilMock;

    private static final long USER_ID = 42L;
    private static final long TENANT_ID = 5L;
    private static final String VALID_TOKEN = "abc123token";

    @BeforeEach
    void setUp() {
        interceptor = new TenantHandshakeInterceptor();
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

    @Test
    @DisplayName("合法 token 从 query param → 握手成功，attributes 含 userId 和 tenantId")
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
    @DisplayName("合法 token 从 Authorization header → 握手成功")
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
    @DisplayName("无 token → 401 拒绝握手")
    void shouldRejectWhenNoToken() throws Exception {
        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/notify"));
        when(request.getHeaders()).thenReturn(new HttpHeaders());

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        assertThat(attributes).isEmpty();
    }

    @Test
    @DisplayName("无效 token（getLoginIdByToken 抛异常）→ 401 拒绝握手")
    void shouldRejectWhenTokenIsInvalid() throws Exception {
        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/notify?token=invalid-token"));
        when(request.getHeaders()).thenReturn(new HttpHeaders());

        stpUtilMock.when(() -> StpUtil.getLoginIdByToken("invalid-token"))
                .thenThrow(new RuntimeException("token invalid"));

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        assertThat(attributes).isEmpty();
    }

    @Test
    @DisplayName("token 有效但 getLoginIdByToken 返回 null → 401 拒绝握手")
    void shouldRejectWhenLoginIdIsNull() throws Exception {
        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/notify?token=" + VALID_TOKEN));
        when(request.getHeaders()).thenReturn(new HttpHeaders());

        stpUtilMock.when(() -> StpUtil.getLoginIdByToken(VALID_TOKEN)).thenReturn(null);

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("token 有效但 loginId 非数字 → 401 拒绝握手")
    void shouldRejectWhenLoginIdIsNotNumeric() throws Exception {
        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/notify?token=" + VALID_TOKEN));
        when(request.getHeaders()).thenReturn(new HttpHeaders());

        stpUtilMock.when(() -> StpUtil.getLoginIdByToken(VALID_TOKEN)).thenReturn("not-a-number");

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("token 有效但 session 中无 tenantId → 401 拒绝握手")
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
        assertThat(attributes).isEmpty();
    }

    @Test
    @DisplayName("query param 中 token 为空值 → 回退到 header，header 也无 → 401")
    void shouldRejectWhenTokenParamIsEmpty() throws Exception {
        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/notify?token="));
        when(request.getHeaders()).thenReturn(new HttpHeaders());

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("query param 中有多个参数，token 在中间 → 正确提取")
    void shouldExtractTokenFromMiddleOfQueryString() throws Exception {
        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/notify?foo=bar&token=" + VALID_TOKEN + "&baz=qux"));
        when(request.getHeaders()).thenReturn(new HttpHeaders());

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
    @DisplayName("tenantId 为 Integer 类型（Number 子类）→ 正确转换为 long")
    void shouldHandleIntegerTenantId() throws Exception {
        when(request.getURI()).thenReturn(new URI("ws://localhost/ws/notify?token=" + VALID_TOKEN));
        when(request.getHeaders()).thenReturn(new HttpHeaders());

        stpUtilMock.when(() -> StpUtil.getLoginIdByToken(VALID_TOKEN)).thenReturn(USER_ID);
        SaSession session = mock(SaSession.class);
        stpUtilMock.when(() -> StpUtil.getSessionByLoginId(USER_ID)).thenReturn(session);
        // Sa-Token session 可能存储为 Integer
        when(session.get("tenantId")).thenReturn(Integer.valueOf(3));

        boolean result = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(result).isTrue();
        assertThat(attributes.get("tenantId")).isEqualTo(3L);
    }
}
