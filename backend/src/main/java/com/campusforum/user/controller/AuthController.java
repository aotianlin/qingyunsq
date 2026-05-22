package com.campusforum.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.common.R;
import com.campusforum.infra.security.WsTicketService;
import com.campusforum.user.dto.ChangePasswordRequest;
import com.campusforum.user.dto.LoginRequest;
import com.campusforum.user.dto.RegisterRequest;
import com.campusforum.user.dto.UserVO;
import com.campusforum.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final WsTicketService wsTicketService;

    @PostMapping("/register")
    public R<UserVO> register(@Valid @RequestBody RegisterRequest req) {
        UserVO user = userService.register(req);
        return R.ok(user);
    }

    @PostMapping("/login")
    public R<Map<String, Object>> login(@Valid @RequestBody LoginRequest req) {
        UserVO user = userService.login(req);
        String token = StpUtil.getTokenValue();
        return R.ok(Map.of(
                "token", token,
                "user", user
        ));
    }

    @PostMapping("/logout")
    public R<?> logout() {
        userService.logout();
        return R.ok();
    }

    @GetMapping("/me")
    public R<UserVO> me() {
        long userId = StpUtil.getLoginIdAsLong();
        UserVO user = userService.getById(userId);
        return R.ok(user);
    }

    @PutMapping("/password")
    public R<?> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        long userId = StpUtil.getLoginIdAsLong();
        userService.changePassword(userId, req.getOldPassword(), req.getNewPassword());
        return R.ok();
    }

    @PostMapping("/forgot-password")
    public R<Map<String, String>> forgotPassword(@RequestBody Map<String, String> body) {
        // 无论邮箱是否存在，统一返回相同响应，防止用户枚举攻击
        // token 不通过 HTTP 响应返回，应通过邮件发送（当前为 mock，仅记录日志）
        try {
            userService.forgotPassword(body.get("email"));
        } catch (Exception ignored) {
            // 故意忽略异常，防止通过错误响应枚举用户
        }
        return R.ok(Map.of("message", "如该邮箱已注册，重置链接将发送至您的邮箱"));
    }

    @PostMapping("/reset-password")
    public R<?> resetPassword(@RequestBody Map<String, String> body) {
        userService.resetPassword(body.get("email"), body.get("token"), body.get("newPassword"));
        return R.ok();
    }

    /**
     * 颁发 WebSocket 一次性票据。
     *
     * <p>客户端在建立 WebSocket 连接前调用本接口拿到短期票据，
     * 用 {@code ?ticket=xxx} 替代将 Sa-Token 主令牌写入 URL，
     * 避免主令牌泄漏到 nginx access log / 浏览器历史 / Referer 头。</p>
     */
    @PostMapping("/ws-ticket")
    public R<Map<String, Object>> wsTicket() {
        long userId = StpUtil.getLoginIdAsLong();
        Object tid = StpUtil.getSession().get("tenantId");
        if (!(tid instanceof Number)) {
            // session 中 tenantId 缺失视为异常，要求重新登录
            throw new com.campusforum.common.BusinessException(
                    com.campusforum.common.ErrorCode.UNAUTHORIZED);
        }
        WsTicketService.Ticket t = wsTicketService.issue(userId, ((Number) tid).longValue());
        return R.ok(Map.of(
                "ticket", t.token(),
                "expiresAt", t.expiresAtSeconds()
        ));
    }
}
