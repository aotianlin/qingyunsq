package com.campusforum.notify.websocket;

import com.campusforum.tenant.websocket.TenantHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotifyWebSocketHandler handler;
    private final TenantHandshakeInterceptor tenantHandshakeInterceptor;

    public WebSocketConfig(NotifyWebSocketHandler handler,
                           TenantHandshakeInterceptor tenantHandshakeInterceptor) {
        this.handler = handler;
        this.tenantHandshakeInterceptor = tenantHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/notify")
                .addInterceptors(tenantHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
