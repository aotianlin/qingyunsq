package com.campusforum.tenant.websocket;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;

/**
 * WebSocket 握手拦截器：从 query param 或 Authorization header 提取 Sa-Token，
 * 验证有效性并从 Session 读取 tenantId 写入 attributes。
 * token 缺失/无效/session 缺 tenantId 时返回 401 拒绝握手。
 */
@Component
@RequiredArgsConstructor
public class TenantHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler,
                                    Map<String, Object> attributes) {
        // 1. 提取 token
        String token = extractToken(request);
        if (token == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        // 2. 验证 token 有效性
        Object loginId;
        try {
            loginId = StpUtil.getLoginIdByToken(token);
        } catch (Exception e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        if (loginId == null || !loginId.toString().matches("\\d+")) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        long userId = Long.parseLong(loginId.toString());

        // 3. 从 Sa-Token Session 读取 tenantId
        Object tid;
        try {
            tid = StpUtil.getSessionByLoginId(userId).get("tenantId");
        } catch (Exception e) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        if (tid == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        // 4. 写入 attributes 供 WebSocketHandler 使用
        attributes.put("userId", userId);
        attributes.put("tenantId", ((Number) tid).longValue());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    /**
     * 优先从 query string ?token=xxx 提取（浏览器原生 WebSocket API 不支持自定义 header），
     * 其次从 Authorization header 提取。
     */
    private String extractToken(ServerHttpRequest request) {
        String query = request.getURI().getQuery();
        if (query != null) {
            for (String kv : query.split("&")) {
                if (kv.startsWith("token=")) {
                    String value = kv.substring(6);
                    if (!value.isEmpty()) {
                        return value;
                    }
                }
            }
        }
        List<String> auth = request.getHeaders().get("Authorization");
        return (auth != null && !auth.isEmpty()) ? auth.get(0) : null;
    }
}
