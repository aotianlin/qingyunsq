package com.campusforum.tenant;

/**
 * 租户上下文缺失异常（任务 T9.4 / 漏洞 28）。
 *
 * <p><b>背景</b>：早期 {@code MyBatisPlusConfig} 在 {@link TenantContext#getTenantId()}
 * 为 {@code null} 时抛 {@link IllegalStateException}，{@code GlobalExceptionHandler}
 * 通过 {@code message.contains("TenantContext is null")} 字符串匹配把它特化为
 * 503 响应。这种"魔法字符串"做异常分类有以下问题：</p>
 * <ul>
 *   <li>消息一旦被翻译 / 改写就静默退化为 500，前端展示反而更丑；</li>
 *   <li>无法被 IDE / 静态分析识别为"业务异常"，重构容易遗漏；</li>
 *   <li>攻击者构造能拼接出 "TenantContext is null" 子串的入参时，可能误触发
 *       本应返回 5xx 的路径降级为 503，干扰监控告警阈值。</li>
 * </ul>
 *
 * <p><b>设计</b>：用类型化异常代替字符串匹配。所有"租户上下文缺失"路径都改抛
 * 这个异常；{@code GlobalExceptionHandler} 添加专门的 handler 把它翻译为
 * {@code 503 SERVICE_UNAVAILABLE}，原 {@link IllegalStateException} 兜底分支
 * 不再做字符串特化，恢复为 "未知 5xx → 500" 的简单语义。</p>
 */
public class TenantContextMissingException extends RuntimeException {

    private static final String MESSAGE_PREFIX = "TenantContext is null: ";

    /**
     * @param detail 触发原因，例如 "MyBatisPlusConfig.getTenantId" / "scheduled task entry"，
     *               用于日志定位上游入口；前缀 {@code TenantContext is null:} 由本类统一注入。
     */
    public TenantContextMissingException(String detail) {
        super(MESSAGE_PREFIX + (detail == null ? "(no detail)" : detail));
    }
}
