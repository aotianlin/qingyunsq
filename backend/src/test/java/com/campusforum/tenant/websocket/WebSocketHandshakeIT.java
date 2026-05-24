package com.campusforum.tenant.websocket;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.session.TokenSign;
import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WebSocket 握手集成测试：使用 StandardWebSocketClient 验证 TenantHandshakeInterceptor
 * 在真实 WebSocket 握手流程中的行为。
 *
 * <p>使用最小化 Spring Boot 上下文（排除 DB/Redis 自动配置），
 * 通过 Sa-Token 内存 DAO 实现真实的 token 登录/验证流程，
 * 验证合法/非法 token 的握手结果。</p>
 *
 * <p><b>Validates: AC-5.1, AC-5.2</b></p>
 */
class WebSocketHandshakeIT {

    private static ConfigurableApplicationContext context;
    private static int port;

    private StandardWebSocketClient client;

    private static final long USER_ID = 42L;
    private static final long TENANT_ID = 5L;
    private static final String INVALID_TOKEN = "invalid-token-xyz";

    /** 服务端 handler 捕获的 attributes，用于验证 userId/tenantId 写入 */
    static final AtomicReference<Map<String, Object>> capturedAttributes = new AtomicReference<>();

    @Configuration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            RedisAutoConfiguration.class
    })
    @EnableWebSocket
    static class TestApp implements WebSocketConfigurer {

        @Bean
        public SaTokenDao saTokenDao() {
            // 使用内存 DAO 替代 Redis DAO，避免 Redis 依赖
            return new SaTokenDaoDefaultImpl();
        }

        @Bean
        public com.campusforum.infra.security.SecurityProperties securityProperties() {
            // 测试场景：默认不强制 ticket，允许旧 token 兼容路径
            com.campusforum.infra.security.SecurityProperties p =
                    new com.campusforum.infra.security.SecurityProperties();
            p.getWsTicket().setEnforced(false);
            return p;
        }

        @Bean
        public com.campusforum.infra.security.SignedUrlService signedUrlService(
                com.campusforum.infra.security.SecurityProperties props) {
            return new com.campusforum.infra.security.SignedUrlService(props);
        }

        @Bean
        public com.campusforum.infra.security.WsTicketService wsTicketService(
                com.campusforum.infra.security.SignedUrlService signedUrlService,
                com.campusforum.infra.security.SecurityProperties props) {
            return new com.campusforum.infra.security.WsTicketService(signedUrlService, props);
        }

        @Bean
        public com.campusforum.infra.metrics.SecurityMetrics securityMetrics() {
            return new com.campusforum.infra.metrics.SecurityMetrics(
                    new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        }

        @Bean
        public TenantHandshakeInterceptor tenantHandshakeInterceptor(
                com.campusforum.infra.security.WsTicketService wsTicketService,
                com.campusforum.infra.security.SecurityProperties props,
                com.campusforum.infra.metrics.SecurityMetrics securityMetrics) {
            return new TenantHandshakeInterceptor(wsTicketService, props, securityMetrics);
        }

        @Bean
        public WebSocketHandler testHandler() {
            return new TextWebSocketHandler() {
                @Override
                public void afterConnectionEstablished(WebSocketSession session) {
                    capturedAttributes.set(session.getAttributes());
                }
            };
        }

        @Override
        public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
            // 修复：原来通过 context.getBean(...) 获取 interceptor，
            // 但 registerWebSocketHandlers() 在 Spring 容器 refresh 阶段被调用，
            // 此时 @BeforeAll 的 app.run() 尚未返回，static context 仍为 null，
            // 直接 NPE。改为复用上面已经定义的 @Bean 链路：手动构造一份
            // SecurityProperties + WsTicketService + SecurityMetrics 即可。
            com.campusforum.infra.security.SecurityProperties p = securityProperties();
            com.campusforum.infra.security.SignedUrlService sus = signedUrlService(p);
            com.campusforum.infra.security.WsTicketService wts = wsTicketService(sus, p);
            com.campusforum.infra.metrics.SecurityMetrics sm = securityMetrics();
            registry.addHandler(testHandler(), "/ws/notify")
                    .addInterceptors(new TenantHandshakeInterceptor(wts, p, sm))
                    .setAllowedOrigins("*");
        }
    }

    @BeforeAll
    static void startServer() {
        SpringApplication app = new SpringApplication(TestApp.class);
        app.setDefaultProperties(Map.of(
                "server.port", "0",
                "sa-token.token-name", "Authorization",
                "sa-token.timeout", "3600",
                "spring.autoconfigure.exclude", "cn.dev33.satoken.dao.SaTokenDaoRedisJackson"
        ));
        context = app.run();
        port = context.getEnvironment().getProperty("local.server.port", Integer.class, 0);
    }

    @AfterAll
    static void stopServer() {
        if (context != null) {
            context.close();
        }
    }

    @BeforeEach
    void setUp() {
        client = new StandardWebSocketClient();
        capturedAttributes.set(null);
    }

    @AfterEach
    void tearDown() {
        // 登出测试用户（如果已登录）
        try {
            if (StpUtil.isLogin(USER_ID)) {
                StpUtil.logout(USER_ID);
            }
        } catch (Exception ignored) {}
    }

    /**
     * 模拟用户登录并在 session 中写入 tenantId，返回 token。
     * 使用 Sa-Token 的底层 API 避免需要 web 上下文。
     */
    private String loginAndGetToken(long userId, long tenantId) {
        cn.dev33.satoken.stp.StpLogic stpLogic = StpUtil.stpLogic;
        String tokenValue = stpLogic.createTokenValue(userId, null, -1, null);

        // 保存 token → loginId 映射
        SaManager.getSaTokenDao().set(stpLogic.splicingKeyTokenValue(tokenValue), String.valueOf(userId), SaManager.getConfig().getTimeout());
        // 保存 loginId → token 映射（token-session）
        SaSession tokenSession = stpLogic.getSessionByLoginId(userId, true);
        tokenSession.addTokenSign(new TokenSign(tokenValue, "login", null));

        // 写入 tenantId 到 session
        SaSession session = stpLogic.getSessionByLoginId(userId, true);
        session.set("tenantId", tenantId);

        return tokenValue;
    }

    @Test
    @DisplayName("15.1/15.2 合法 token 从 query param 握手成功，attributes 中 userId 和 tenantId 正确")
    void shouldSucceedHandshakeWithValidToken() throws Exception {
        String token = loginAndGetToken(USER_ID, TENANT_ID);

        URI uri = new URI("ws://localhost:" + port + "/ws/notify?token=" + token);

        CompletableFuture<WebSocketSession> sessionFuture = new CompletableFuture<>();
        TextWebSocketHandler clientHandler = new TextWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) {
                sessionFuture.complete(session);
            }
        };

        WebSocketSession wsSession = client
                .execute(clientHandler, new WebSocketHttpHeaders(), uri)
                .get(5, TimeUnit.SECONDS);

        assertThat(wsSession).isNotNull();
        assertThat(wsSession.isOpen()).isTrue();

        // 等待服务端 handler 处理
        Thread.sleep(200);

        // 验证服务端 attributes 中 userId 和 tenantId 正确
        Map<String, Object> attrs = capturedAttributes.get();
        assertThat(attrs).isNotNull();
        assertThat(attrs.get("userId")).isEqualTo(USER_ID);
        assertThat(attrs.get("tenantId")).isEqualTo(TENANT_ID);

        wsSession.close();
    }

    @Test
    @DisplayName("15.1/15.2 合法 token 通过 Authorization header 握手成功")
    void shouldSucceedHandshakeWithValidTokenInHeader() throws Exception {
        String token = loginAndGetToken(USER_ID, TENANT_ID);

        URI uri = new URI("ws://localhost:" + port + "/ws/notify");
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Authorization", token);

        CompletableFuture<WebSocketSession> sessionFuture = new CompletableFuture<>();
        TextWebSocketHandler clientHandler = new TextWebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) {
                sessionFuture.complete(session);
            }
        };

        WebSocketSession wsSession = client
                .execute(clientHandler, headers, uri)
                .get(5, TimeUnit.SECONDS);

        assertThat(wsSession).isNotNull();
        assertThat(wsSession.isOpen()).isTrue();

        Thread.sleep(200);

        // 验证 attributes
        Map<String, Object> attrs = capturedAttributes.get();
        assertThat(attrs).isNotNull();
        assertThat(attrs.get("userId")).isEqualTo(USER_ID);
        assertThat(attrs.get("tenantId")).isEqualTo(TENANT_ID);

        wsSession.close();
    }

    @Test
    @DisplayName("15.3 非法 token 时握手失败（HTTP 401）")
    void shouldRejectHandshakeWithInvalidToken() {
        URI uri = URI.create("ws://localhost:" + port + "/ws/notify?token=" + INVALID_TOKEN);

        TextWebSocketHandler clientHandler = new TextWebSocketHandler() {};

        // 握手失败：服务端返回 401，客户端抛异常
        assertThatThrownBy(() ->
                client.execute(clientHandler, new WebSocketHttpHeaders(), uri)
                        .get(5, TimeUnit.SECONDS)
        ).satisfiesAnyOf(
                e -> assertThat(e).isInstanceOf(ExecutionException.class),
                e -> assertThat(e).isInstanceOf(TimeoutException.class)
        );

        // 验证 attributes 未被写入（握手被拒绝）
        assertThat(capturedAttributes.get()).isNull();
    }

    @Test
    @DisplayName("15.3 无 token 时握手失败")
    void shouldRejectHandshakeWithNoToken() {
        URI uri = URI.create("ws://localhost:" + port + "/ws/notify");

        TextWebSocketHandler clientHandler = new TextWebSocketHandler() {};

        assertThatThrownBy(() ->
                client.execute(clientHandler, new WebSocketHttpHeaders(), uri)
                        .get(5, TimeUnit.SECONDS)
        ).satisfiesAnyOf(
                e -> assertThat(e).isInstanceOf(ExecutionException.class),
                e -> assertThat(e).isInstanceOf(TimeoutException.class)
        );

        assertThat(capturedAttributes.get()).isNull();
    }

    @Test
    @DisplayName("15.3 token 有效但 session 中无 tenantId 时握手失败")
    void shouldRejectHandshakeWhenSessionMissingTenantId() {
        // 登录但不写入 tenantId（使用底层 API 避免 web 上下文依赖）
        cn.dev33.satoken.stp.StpLogic stpLogic = StpUtil.stpLogic;
        String tokenValue = stpLogic.createTokenValue(USER_ID, null, -1, null);
        SaManager.getSaTokenDao().set(stpLogic.splicingKeyTokenValue(tokenValue), String.valueOf(USER_ID), SaManager.getConfig().getTimeout());
        SaSession tokenSession = stpLogic.getSessionByLoginId(USER_ID, true);
        tokenSession.addTokenSign(new TokenSign(tokenValue, "login", null));
        // 注意：不写入 tenantId

        URI uri = URI.create("ws://localhost:" + port + "/ws/notify?token=" + tokenValue);

        TextWebSocketHandler clientHandler = new TextWebSocketHandler() {};

        assertThatThrownBy(() ->
                client.execute(clientHandler, new WebSocketHttpHeaders(), uri)
                        .get(5, TimeUnit.SECONDS)
        ).satisfiesAnyOf(
                e -> assertThat(e).isInstanceOf(ExecutionException.class),
                e -> assertThat(e).isInstanceOf(TimeoutException.class)
        );

        assertThat(capturedAttributes.get()).isNull();
    }
}
