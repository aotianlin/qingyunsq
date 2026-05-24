package com.campusforum.infra.audit;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.admin.domain.AuditLog;
import com.campusforum.admin.dto.AuditLogVO;
import com.campusforum.admin.mapper.AuditLogMapper;
import com.campusforum.infra.security.TrustedProxyResolver;
import com.campusforum.tenant.TenantContext;
import com.campusforum.user.domain.User;
import com.campusforum.user.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/**
 * 审计日志服务。
 *
 * <p><b>背景（对应 bugfix.md 漏洞 26）</b>：早期 {@code AuditLogService} 通过
 * Spring 注入 request-scope 的 {@link HttpServletRequest} 代理对象获取客户端 IP，
 * 在 {@code @Async} 异步线程或定时任务等"无 Servlet 线程上下文"的场景下，
 * 该代理会抛出 {@link IllegalStateException}，导致：</p>
 * <ul>
 *   <li>异步流程审计完全失败（IP 字段为空甚至整条日志写不进去）；</li>
 *   <li>调用方被迫在线程内手动捕获异常，污染业务代码；</li>
 *   <li>事件溯源缺失关键 IP / UA / traceId 信息。</li>
 * </ul>
 *
 * <p><b>设计变更</b>：引入显式 {@link AuditContext} 上下文对象，把"操作者 / 租户 /
 * 客户端 IP / User-Agent"打包传入。新写代码必须使用 5 参重载
 * {@link #log(AuditContext, String, String, Long, String)}；旧 4 参方法保留为
 * {@code @Deprecated} 兼容入口，内部通过 {@link RequestContextHolder} 兜底
 * 获取 request，失败时再退回 {@link MDC} 读取 traceId / userId / tenantId / clientIp 拼装
 * 一份"尽力而为"的 AuditContext，保证不抛异常吞掉审计调用。</p>
 *
 * <p><b>为什么必须显式传 context 而不是依赖 RequestContextHolder</b>：</p>
 * <ul>
 *   <li>{@link RequestContextHolder} 是 ThreadLocal-based，{@code @Async} 默认
 *       不复制 ThreadLocal（需要 {@code RequestContextHolder.setRequestAttributes
 *       (..., true)} 显式 inheritable）；</li>
 *   <li>WebSocket / 定时任务 / 消息队列消费者 等线程根本就没有
 *       {@link HttpServletRequest}，注入的代理在调用任意方法时会立即抛
 *       {@link IllegalStateException};</li>
 *   <li>显式 context 让"是谁/在哪/做了什么"在编译期就由调用方保证，避免
 *       运行期才暴露丢失。</li>
 * </ul>
 *
 * <p>调用方迁移路径见 T3.1 / T9.4 等任务，本任务只提供 API，不强制立刻迁移。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;
    private final UserMapper userMapper;
    /**
     * Servlet request 代理（仅旧 4 参兼容路径会真正解引用），构造期不会触发异常，
     * 异步线程内调用其方法时才会抛 {@link IllegalStateException}。
     */
    private final HttpServletRequest request;
    private final TrustedProxyResolver trustedProxyResolver;

    /**
     * 推荐入口：基于显式 {@link AuditContext} 写审计日志。
     *
     * <p>该重载是异步 / 非 Servlet 线程下唯一可靠的入口。同步 Servlet 线程也
     * 推荐使用此重载，可让调用方明确传入"我此刻知道的操作者 / 租户 / IP / UA"，
     * 避免依赖隐式 ThreadLocal。</p>
     *
     * @param ctx        审计上下文，必须非空（包含 operatorId / tenantId / clientIp / userAgent）
     * @param action     操作类型（如 {@code USER_BAN}、{@code PASSWORD_CHANGE}）
     * @param targetType 操作对象类型（如 {@code user}、{@code post}）
     * @param targetId   操作对象 ID（可空）
     * @param detail     操作详情（可空）
     */
    @Transactional
    public void log(AuditContext ctx, String action, String targetType, Long targetId, String detail) {
        if (ctx == null) {
            // 兜底：构造一个空上下文，仍然把动作写进去，但不抛异常
            ctx = AuditContext.builder().build();
        }
        AuditLog entry = new AuditLog();
        entry.setOperatorId(ctx.getOperatorId());
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        // audit_logs.detail 是 JSON 列；调用方传入的多为半结构化文本（如 "role changed to ADMIN"），
        // 直接写入会被 MySQL 拒绝（"Invalid JSON text: Invalid value."）。
        // 这里统一把 null 透传、把已是 JSON 的内容透传、其余字符串包成 JSON string。
        entry.setDetail(toJsonStringSafe(detail));
        entry.setIpAddress(ctx.getClientIp());
        entry.setUserAgent(truncateUserAgent(ctx.getUserAgent()));

        // tenantId 由 MyBatis-Plus 多租户拦截器从 TenantContext 自动注入；
        // 当 ctx 显式带 tenantId 时建议调用方提前 setTenantId，确保异步线程也能写入。
        // 这里不主动 set 是因为 audit_logs 表的 tenant_id 列由拦截器统一改写。
        auditLogMapper.insert(entry);
        log.debug("Audit log recorded: action={}, operator={}, ip={}",
                action, entry.getOperatorId(), entry.getIpAddress());
    }

    /**
     * 旧 4 参兼容入口：依赖 {@link RequestContextHolder} 隐式获取 request。
     *
     * <p>建议新代码一律改为 {@link #log(AuditContext, String, String, Long, String)}。
     * 本方法在 {@code @Async} 线程下会自动回退到 MDC 兜底，不再抛
     * {@link IllegalStateException}。</p>
     *
     * <p>迁移计划：旧 controller / service 调用点会在后续主题任务（T3.1 等）
     * 逐步切换到 5 参版本；此方法保留至少到 2026-09-01。</p>
     *
     * @deprecated 请使用 {@link #log(AuditContext, String, String, Long, String)} 显式传 context。
     */
    @Deprecated
    @Transactional
    public void log(String action, String targetType, Long targetId, String detail) {
        log(currentRequestContext(), action, targetType, targetId, detail);
    }

    /**
     * 构造"当前请求"对应的 AuditContext，作为旧 4 参 API 的兜底。
     *
     * <p>解析顺序：</p>
     * <ol>
     *   <li>先尝试从 {@link RequestContextHolder} 取 {@link ServletRequestAttributes}，
     *       命中时基于真实 request 构造（可拿到 IP / UA）；</li>
     *   <li>失败（异步线程 / 无 Servlet 线程）则退到 {@link MDC} 读取
     *       {@code userId} / {@code tenantId} / {@code clientIp}，至少把"操作者"信息
     *       保留下来；</li>
     *   <li>都拿不到时返回空白 ctx，但调用方 {@link #log(AuditContext, String, String, Long, String)}
     *       仍会写入一条审计（IP/UA 字段为空），便于事后对账。</li>
     * </ol>
     */
    private AuditContext currentRequestContext() {
        // 1) 先试 Servlet 线程
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                HttpServletRequest req = sra.getRequest();
                Long operatorId = safeOperatorIdFromStp();
                Long tenantId = TenantContext.getTenantId();
                return AuditContext.from(req, trustedProxyResolver, operatorId, tenantId);
            }
        } catch (Exception e) {
            log.debug("RequestContextHolder unavailable, fallback to MDC: {}", e.getMessage());
        }
        // 2) 异步线程：从 MDC 兜底
        return AuditContext.builder()
                .operatorId(parseLongQuiet(MDC.get("userId")))
                .tenantId(parseLongQuiet(MDC.get("tenantId")))
                .clientIp(MDC.get("clientIp"))
                .userAgent(null)
                .build();
    }

    /** 静默从 Sa-Token 取 loginId；若无登录上下文则返回 null，避免异步线程抛异常。 */
    private Long safeOperatorIdFromStp() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return null;
        }
    }

    /** 容错解析 {@link Long}：null / 空 / 非数字一律返回 null。 */
    private Long parseLongQuiet(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * audit_logs.user_agent 列长度为 255，过长 UA 会触发 SQL 截断警告或报错，
     * 这里先在应用层主动截断。
     */
    private String truncateUserAgent(String ua) {
        if (ua == null) return null;
        return ua.length() > 255 ? ua.substring(0, 255) : ua;
    }

    /**
     * 把任意文本安全转换为 JSON 字符串值（双引号包裹 + 内部转义），
     * 以便写入 {@code audit_logs.detail JSON} 列。
     *
     * <p>背景：所有调用方传入的 detail 多为半结构化文本，直接写入 JSON 列会被
     * MySQL 拒绝（{@code Invalid JSON text: "Invalid value." at position 0}）。
     * 本方法的契约：</p>
     * <ul>
     *   <li>{@code null} 透传，保持列为 NULL；</li>
     *   <li>已是合法 JSON（以 {@code {} } / {@code []} / {@code "..."} 开头）的字符串原样透传；</li>
     *   <li>其他纯文本一律包装为 JSON 字符串字面量（双引号 + 内部 {@code \"} {@code \\} {@code \n} {@code \r} {@code \t} 转义）。</li>
     * </ul>
     */
    private static String toJsonStringSafe(String detail) {
        if (detail == null) return null;
        String trimmed = detail.stripLeading();
        if (!trimmed.isEmpty()) {
            char first = trimmed.charAt(0);
            // 已经是 JSON 对象 / 数组 / 字符串，原样透传（信任调用方）
            if (first == '{' || first == '[' || first == '"') {
                return detail;
            }
        }
        // 其余按 JSON 字符串字面量编码
        StringBuilder sb = new StringBuilder(detail.length() + 8);
        sb.append('"');
        for (int i = 0; i < detail.length(); i++) {
            char c = detail.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        // 控制字符按 JSON 规范转义为 backslash-u + 4 位十六进制 形式。
                        // 这里把字面量拆开拼接，避免 javac 在源代码扫描阶段
                        // 把 backslash u 当成"非法 Unicode 转义"报错。
                        sb.append('\\').append('u').append(String.format("%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    public List<AuditLogVO> page(Long cursor, int limit, Long operatorId, String action) {
        int size = Math.min(limit, 50);
        LambdaQueryWrapper<AuditLog> qw = new LambdaQueryWrapper<>();
        if (cursor != null) {
            qw.lt(AuditLog::getId, cursor);
        }
        if (operatorId != null) {
            qw.eq(AuditLog::getOperatorId, operatorId);
        }
        if (action != null && !action.isBlank()) {
            qw.eq(AuditLog::getAction, action);
        }
        qw.orderByDesc(AuditLog::getId);
        qw.last("LIMIT " + size);

        return auditLogMapper.selectList(qw).stream().map(this::toVO).toList();
    }

    private AuditLogVO toVO(AuditLog log) {
        String operatorName = null;
        if (log.getOperatorId() != null) {
            User operator = userMapper.selectById(log.getOperatorId());
            if (operator != null) {
                operatorName = operator.getNickname();
            }
        }

        return AuditLogVO.builder()
                .id(log.getId())
                .operatorId(log.getOperatorId())
                .operatorName(operatorName)
                .action(log.getAction())
                .targetType(log.getTargetType())
                .targetId(log.getTargetId())
                .detail(log.getDetail())
                .ipAddress(log.getIpAddress())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
