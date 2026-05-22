package com.campusforum.common;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldMapSaTokenNotLoginToUnauthorized() {
        R<?> response = handler.handleNotLogin(new NotLoginException("未登录", "login", NotLoginException.NOT_TOKEN));

        assertThat(response.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED.getCode());
        assertThat(response.getMessage()).isEqualTo(ErrorCode.UNAUTHORIZED.getMessage());
    }

    @Test
    void shouldMapSaTokenPermissionFailureToForbidden() {
        R<?> response = handler.handleForbidden(new NotPermissionException("admin"));

        assertThat(response.getCode()).isEqualTo(ErrorCode.FORBIDDEN.getCode());
        assertThat(response.getMessage()).isEqualTo(ErrorCode.FORBIDDEN.getMessage());
    }

    @Test
    void shouldMapTenantContextMissingTo503() {
        // 安全加固后：TenantContext 缺失场景返回 503，避免攻击者通过任意路径触发 5xx
        ResponseEntity<R<?>> response = handler.handleIllegalState(
                new IllegalStateException("TenantContext is null"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE.getCode());
    }

    @Test
    void shouldMapOtherIllegalStateToInternalError() {
        ResponseEntity<R<?>> response = handler.handleIllegalState(
                new IllegalStateException("其他基础设施异常"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR.getCode());
        assertThat(response.getBody().getMessage()).isEqualTo("服务器内部错误");
    }
}
