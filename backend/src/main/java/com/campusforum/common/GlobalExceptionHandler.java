package com.campusforum.common;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.campusforum.infra.security.CryptoException;
import com.campusforum.infra.security.MimeMismatchException;
import com.campusforum.infra.security.SSRFBlockedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public R<?> handleBusiness(BusinessException e) {
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(NotLoginException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R<?> handleNotLogin(NotLoginException e) {
        // Sa-Token 会在拦截器阶段抛出未登录异常；这里显式转成 401，避免被兜底处理成 500。
        return R.fail(ErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<?> handleForbidden(Exception e) {
        // 权限和角色校验失败都属于已认证但无操作权限，前端据此展示无权限状态。
        return R.fail(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<?> handleBind(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        return R.fail(ErrorCode.BAD_REQUEST.getCode(), msg);
    }

    @ExceptionHandler(SSRFBlockedException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<?> handleSsrf(SSRFBlockedException e) {
        // 出站请求命中私网，记录后返回通用错误码，不暴露具体 host
        log.warn("SSRF blocked: {}", e.getMessage());
        return R.fail(ErrorCode.SSRF_BLOCKED);
    }

    @ExceptionHandler(MimeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<?> handleMimeMismatch(MimeMismatchException e) {
        return R.fail(ErrorCode.MIME_MISMATCH.getCode(), e.getMessage());
    }

    @ExceptionHandler(CryptoException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<?> handleCrypto(CryptoException e) {
        // 加解密异常可能涉及密钥/密文细节，仅记录服务端日志，不向客户端暴露
        log.error("Crypto failure: {}", e.getMessage());
        return R.fail(ErrorCode.CRYPTO_FAILURE);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<R<?>> handleIllegalState(IllegalStateException e) {
        // TenantContext 缺失等基础设施异常映射为 503，避免攻击者通过任意路径触发 5xx
        log.error("IllegalStateException caught: {}", e.getMessage(), e);
        if (e.getMessage() != null && e.getMessage().contains("TenantContext is null")) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(R.fail(ErrorCode.SERVICE_UNAVAILABLE));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.fail(ErrorCode.INTERNAL_ERROR.getCode(), "服务器内部错误"));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<?> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return R.fail(ErrorCode.INTERNAL_ERROR);
    }
}
