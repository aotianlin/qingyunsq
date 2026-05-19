package com.campusforum.common;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import org.junit.jupiter.api.Test;

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
    void shouldMapIllegalStateExceptionToInternalError() {
        R<?> response = handler.handleIllegalState(
                new IllegalStateException("TenantContext is null"));

        assertThat(response.getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR.getCode());
        assertThat(response.getMessage()).isEqualTo("服务器内部错误");
    }
}
