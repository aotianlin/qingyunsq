package com.campusforum.tenant.websocket;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.infra.security.SecurityProperties;
import com.campusforum.infra.security.WsTicketService;
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
 * WebSocket 握手拦截器：优先识别一次性 {@code ticket} 参数（推荐），未提供时回退到旧
 * {@code token}（兼容期）。
 *
 * <p>验证有效性后将 userId/tenantId 写入 attributes，供 WebSocketHandler 后续使用。
 * 全部失败时返回 401 拒绝握手。</p>
 *
 * <p>当 {@code security.ws-ticket.enforced=true} 时拒绝旧 {@code token} 路径，强制使用 ticket。</p>
 */
@Component
@RequiredArgsConstructor
public class TenantHandshakeInterceptor implements HandshakeInterceptor {

    private final WsTicketService wsTicketService;
    private final SecurityProperties securityProperties;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler,
                                    Map<String, Object> attributes) {
        // 1) 优先：ticket 路径（短期、签名、绑定 tenantId）
        String ticket = extractQueryParam(request, "ticket");
        if (ticket != null) {
            WsTicketService.Verified v = wsTicketService.verify(ticket);
            if (v == null) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            attributes.put("userId", v.userId());
            attributes.put("tenantId", v.tenantId());
            return true;
        }

        // 2) 兼容期：token 路径（短期内允许；ws-ticket.enforced=true 时禁用）
        if (securityProperties.getWsTicket().isEnforced()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        return verifyByLegacyToken(request, response, attributes);
    }

    /**
     * 兼容旧版本的 token query 路径。计划在所有客户端切换到 ticket 后下线。
     */
    private boolean verifyByLegacyToken(ServerHttpRequest request, ServerHttpResponse response,
                                        Map<String, Object> attributes) {
        String token = extractToken(request);
        if (token == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

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
     * 从 query string 提取指定参数；找不到返回 null。
     */
    private String extractQueryParam(ServerHttpRequest request, String name) {
        String query = request.getURI().getQuery();
        if (query == null) return null;
        String prefix = name + "=";
        for (String kv : query.split("&")) {
            if (kv.startsWith(prefix)) {
                String value = kv.substring(prefix.length());
                if (!value.isEmpty()) return value;
            }
        }
        return null;
    }

    /**
     * 旧版本 token 提取：优先从 query string ?token=xxx 提取，其次从 Authorization header。
     */
    private String extractToken(ServerHttpRequest request) {
        String fromQuery = extractQueryParam(request, "token");
        if (fromQuery != null) return fromQuery;
        List<String> auth = request.getHeaders().get("Authorization");
        return (auth != null && !auth.isEmpty()) ? auth.get(0) : null;
    }
}
