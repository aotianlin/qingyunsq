package com.campusforum.common;

import lombok.Data;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * 统一响应包装。
 *
 * <p>traceId 提取顺序（对应 bugfix.md 漏洞 31）：</p>
 * <ol>
 *   <li>优先读取 SLF4J {@link MDC} 中的 {@code traceId}，由
 *       {@link com.campusforum.infra.web.MdcTraceIdFilter} 在请求入站时写入；
 *       这样响应体中的 traceId 与服务端日志的 {@code %X{traceId}} 完全一致，
 *       便于事后排查（用户提供 traceId → 日志一键检索）。</li>
 *   <li>当 MDC 中无值（例如异步任务、定时任务、单元测试场景），回退到现场
 *       生成的 8 字符 UUID 片段，保持向后兼容。</li>
 * </ol>
 */
@Data
public class R<T> {

    private int code;
    private String message;
    private T data;
    private String traceId;

    private R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = resolveTraceId();
    }

    /**
     * 解析当前响应使用的 traceId：MDC 优先，缺省时回退到 UUID 片段。
     *
     * @return 最终采用的 traceId
     */
    private static String resolveTraceId() {
        String fromMdc = MDC.get("traceId");
        if (fromMdc != null && !fromMdc.isBlank()) {
            return fromMdc;
        }
        // 兼容旧逻辑：MDC 缺失时仍生成 8 字符 UUID 片段
        return UUID.randomUUID().toString().substring(0, 8);
    }

    public static <T> R<T> ok() {
        return new R<>(0, "ok", null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(0, "ok", data);
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }

    public static <T> R<T> fail(ErrorCode errorCode) {
        return new R<>(errorCode.getCode(), errorCode.getMessage(), null);
    }
}
