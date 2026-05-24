package com.campusforum.common;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.campusforum.infra.security.CryptoException;
import com.campusforum.infra.security.MimeMismatchException;
import com.campusforum.infra.security.SSRFBlockedException;
import com.campusforum.tenant.TenantContextMissingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
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
        return R.fail(ErrorCode.BAD_REQUEST.getCode(), buildFieldErrorMessage(e));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<?> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        return R.fail(ErrorCode.BAD_REQUEST.getCode(), buildFieldErrorMessage(e));
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
        // 任务 T9.4 / 漏洞 28：移除原 "TenantContext is null" 字符串匹配特化，
        // 该路径已改抛专门的 TenantContextMissingException（见下方 handler）。
        // 这里仅作为通用 IllegalStateException 兜底，统一返回 500。
        log.error("IllegalStateException caught: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.fail(ErrorCode.INTERNAL_ERROR.getCode(), "服务器内部错误"));
    }

    /**
     * 租户上下文缺失（任务 T9.4 / 漏洞 28）。
     *
     * <p>典型场景：异步线程 / 定时任务 / WebSocket handler 在未显式
     * {@code TenantContext.setTenantId(...)} 的情况下访问租户表，导致
     * {@code MyBatisPlusConfig#getTenantId} 抛 {@link TenantContextMissingException}。
     * 这种情况属于"基础设施未就绪"，向前端返回 503 让其重试或降级展示。</p>
     */
    @ExceptionHandler(TenantContextMissingException.class)
    public ResponseEntity<R<?>> handleTenantContextMissing(TenantContextMissingException e) {
        log.error("TenantContext missing: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(R.fail(ErrorCode.SERVICE_UNAVAILABLE));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<?> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return R.fail(ErrorCode.INTERNAL_ERROR);
    }

    private String buildFieldErrorMessage(BindException e) {
        return e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
    }
}
