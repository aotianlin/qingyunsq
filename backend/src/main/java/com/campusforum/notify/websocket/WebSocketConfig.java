package com.campusforum.notify.websocket;

import com.campusforum.tenant.websocket.TenantHandshakeInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotifyWebSocketHandler handler;
    private final TenantHandshakeInterceptor tenantHandshakeInterceptor;

    /**
     * 允许的 WebSocket 来源（CSWSH 防御）。逗号分隔，可用通配符 "*" 表示同源；
     * 默认仅本地开发环境，生产部署务必通过环境变量 WS_ALLOWED_ORIGINS 显式覆盖为前端域名。
     */
    @Value("${ws.allowed-origins:http://localhost:3000,http://127.0.0.1:3000}")
    private String allowedOrigins;

    public WebSocketConfig(NotifyWebSocketHandler handler,
                           TenantHandshakeInterceptor tenantHandshakeInterceptor) {
        this.handler = handler;
        this.tenantHandshakeInterceptor = tenantHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        registry.addHandler(handler, "/ws/notify")
                .addInterceptors(tenantHandshakeInterceptor)
                .setAllowedOrigins(origins);
    }
}
