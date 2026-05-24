package com.campusforum.common;

import com.campusforum.tenant.TenantContextMissingException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 任务 T9.4 / 漏洞 28：{@link GlobalExceptionHandler} 对
 * {@link TenantContextMissingException} 的处理验证。
 *
 * <p>纯单元测试：不启动 Spring 上下文，直接 new {@link GlobalExceptionHandler}
 * 并调用对应 handler 方法，断言返回 503 + {@code SERVICE_UNAVAILABLE}。</p>
 */
class TenantContextMissingHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void missingTenant_returns503_serviceUnavailable() {
        // 模拟 MyBatisPlusConfig#getTenantId 抛出的真实异常
        TenantContextMissingException ex = new TenantContextMissingException(
                "MyBatisPlusConfig#getTenantId — missing TenantResolutionFilter");

        ResponseEntity<R<?>> resp = handler.handleTenantContextMissing(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE.getCode());
    }

    @Test
    void illegalState_noTenantStringMatch_returns500_now() {
        // 任务 T9.4 关键回归点：原本字符串匹配特化路径已被移除，
        // 普通 IllegalStateException（即便消息包含 "TenantContext is null"）
        // 也应统一走 500 兜底，避免攻击者构造载荷引发 503。
        IllegalStateException illegal = new IllegalStateException("TenantContext is null. legacy");
        ResponseEntity<R<?>> resp = handler.handleIllegalState(illegal);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR.getCode());
    }

    @Test
    void exceptionMessagePrefix_isInjected() {
        // 异常 message 自动加 "TenantContext is null:" 前缀，便于日志检索
        TenantContextMissingException ex = new TenantContextMissingException("foo");
        assertThat(ex.getMessage()).startsWith("TenantContext is null: ").endsWith("foo");
    }
}
