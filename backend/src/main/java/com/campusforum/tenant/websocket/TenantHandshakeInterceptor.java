package com.campusforum.tenant.websocket;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.infra.metrics.SecurityMetrics;
import com.campusforum.infra.security.SecurityProperties;
import com.campusforum.infra.security.WsTicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket 握手拦截器：优先识别一次性 {@code ticket} 参数（推荐），未提供时回退到旧
 * {@code token}（兼容期）。
 *
 * <p>验证有效性后将 userId/tenantId 写入 attributes，供 WebSocketHandler 后续使用。
 * 全部失败时返回 401 拒绝握手。</p>
 *
 * <p>当 {@code security.ws-ticket.enforced=true} 时拒绝旧 {@code token} 路径，强制使用 ticket。</p>
 *
 * <h2>漏洞 8 / 漏洞 29 加固说明</h2>
 * <ul>
 *   <li><b>漏洞 8（WebSocket legacy token 默认开启 + 主令牌写入 access log）</b>：
 *       为了量化 ticket cutover 进度并提示运维尽快迁移，
 *       {@link #verifyByLegacyToken} 入口会调用
 *       {@link SecurityMetrics#wsLegacyTokenUsed()} 累加计数器
 *       {@code ws_legacy_token_used_total}（每次都计数）；同时配合
 *       {@link #lastWarnAtMillis} + {@link #WARN_THROTTLE_MS} 做"每分钟最多
 *       一条 WARN"限频日志，包含来源 IP 与 User-Agent，避免在被滥用时
 *       打爆磁盘。</li>
 *   <li><b>漏洞 29（query 参数未 URL decode）</b>：原实现用
 *       {@code request.getURI().getQuery()} 取 query string，URI 解析阶段
 *       会自动把 {@code %2B}（+）/ {@code %2F}（/）/ {@code %3D}（=）等
 *       base64 字符 decode，但 {@code +} 又会被部分实现解析为空格，导致
 *       签名 ticket 解析后与签发时不一致从而被 SignedUrlService 拒签。
 *       现改为 {@code getRawQuery()} 拿到未解码的原始 query，再由
 *       {@link URLDecoder#decode(String, java.nio.charset.Charset)} 在
 *       {@link #extractQueryParam} 中统一按 UTF-8 做一次解码，避免双重
 *       解码 / 半解码导致的字符错位。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantHandshakeInterceptor implements HandshakeInterceptor {

    /**
     * legacy WARN 日志最小输出间隔（毫秒）。
     *
     * <p>取 60 秒：旧客户端如果还在大量使用 legacy 路径，
     * 我们只想知道"还有客户端在用"以及"用例的来源 IP / UA 长什么样"，
     * 不需要每条连接都打一条 WARN（否则容易被批量重连刷屏）。</p>
     */
    private static final long WARN_THROTTLE_MS = 60_000L;

    private final WsTicketService wsTicketService;
    private final SecurityProperties securityProperties;
    private final SecurityMetrics securityMetrics;

    /**
     * 上一次输出 legacy WARN 的时间戳（毫秒）。
     *
     * <p>初值 0 让进程启动后第一次进入 legacy 分支立刻打一条 WARN，
     * 后续 60 秒内即使再次命中也只会更新计数器、不再重复输出 WARN。</p>
     *
     * <p>使用 {@link AtomicLong} 而不是 {@code volatile long} 是为了在
     * 多线程并发命中 legacy 分支时通过 CAS 互斥地推进窗口；正确性
     * 不要求"严格 1 次/分钟"，弱保证即可，因此不引入额外锁。</p>
     */
    private final AtomicLong lastWarnAtMillis = new AtomicLong(0L);

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
     *
     * <p>每次进入即累加 {@code ws_legacy_token_used_total} Counter，
     * 同时按 {@link #WARN_THROTTLE_MS} 限频输出 WARN 日志（带来源 IP / UA），
     * 用于运维评估迁移完成度并在 cutover 前定位仍在使用 legacy 的客户端。</p>
     *
     * <p>限频策略：用 {@link AtomicLong#compareAndSet(long, long)} 推进
     * "上次 WARN 的时间戳"窗口，CAS 成功的线程负责输出 WARN，其余线程
     * 仅累加 Counter 然后静默继续验证流程。</p>
     */
    private boolean verifyByLegacyToken(ServerHttpRequest request, ServerHttpResponse response,
                                        Map<String, Object> attributes) {
        // 漏洞 8：每次进入 legacy 分支都累加计数，运维可在 Grafana 上看到
        // 该 Counter 是否已稳定为 0，以便判断何时安全地强制 ticket 模式
        securityMetrics.wsLegacyTokenUsed();

        // 限频 WARN：每分钟最多 1 条，避免被批量重连刷屏
        long now = System.currentTimeMillis();
        long last = lastWarnAtMillis.get();
        if (now - last >= WARN_THROTTLE_MS && lastWarnAtMillis.compareAndSet(last, now)) {
            String ip = resolveRemoteIp(request);
            String ua = request.getHeaders().getFirst("User-Agent");
            log.warn("WS legacy token 路径被使用 ip={}, ua={}, 距 cutover 日期请尽快迁移到 ticket 模式",
                    ip, ua);
        }

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
     *
     * <p>实现要点（对应漏洞 29 修复）：</p>
     * <ol>
     *   <li>使用 {@link java.net.URI#getRawQuery()} 而非 {@code getQuery()}：
     *       前者保留 percent-encoding 原文，后者会被 URI 解析阶段先做一次自动
     *       decode，导致 {@code +} 已经被替换为空格（application/x-www-form-urlencoded
     *       规则差异），split 出来已经不是签发时的字面值。</li>
     *   <li>取出 {@code name=value} 之后用 {@link URLDecoder#decode(String, java.nio.charset.Charset)}
     *       按 UTF-8 统一做一次解码。这样 {@code %2B}（+）/ {@code %2F}（/）/
     *       {@code %3D}（=）等 base64 控制字符可以被还原成 SignedUrlService 签发
     *       时的原文，避免握手 401。</li>
     * </ol>
     */
    private String extractQueryParam(ServerHttpRequest request, String name) {
        // 漏洞 29：必须用 rawQuery，避免 URI 在解析阶段把 + 等字符提前 decode
        String query = request.getURI().getRawQuery();
        if (query == null) return null;
        String prefix = name + "=";
        for (String kv : query.split("&")) {
            if (kv.startsWith(prefix)) {
                String value = kv.substring(prefix.length());
                if (value.isEmpty()) return null;
                // 按 UTF-8 统一 decode 一次，与 encodeURIComponent 配对还原 base64 控制字符
                return URLDecoder.decode(value, StandardCharsets.UTF_8);
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

    /**
     * 解析 WebSocket 握手请求的来源 IP，仅用于 WARN 日志记录。
     *
     * <p>ServerHttpRequest 提供的 {@link ServerHttpRequest#getRemoteAddress()}
     * 在大多数容器实现中是 {@link InetSocketAddress}；该方法不参与权限判断，
     * 故不做 X-Forwarded-For 解析（避免被客户端伪造 IP），仅取 socket 直连地址。</p>
     */
    private String resolveRemoteIp(ServerHttpRequest request) {
        InetSocketAddress remote = request.getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return "unknown";
        }
        return remote.getAddress().getHostAddress();
    }
}
