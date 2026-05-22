package com.campusforum.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.common.R;
import com.campusforum.infra.email.EmailCodeScene;
import com.campusforum.user.dto.EmailCodeRequest;
import com.campusforum.user.dto.EmailOnlyRequest;
import com.campusforum.user.dto.LoginRequest;
import com.campusforum.user.dto.RegisterRequest;
import com.campusforum.user.dto.ResetPasswordRequest;
import com.campusforum.user.dto.UserVO;
import com.campusforum.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/register")
    public R<UserVO> register(@Valid @RequestBody RegisterRequest req) {
        UserVO user = userService.register(req);
        return R.ok(user);
    }

    @PostMapping("/email-code")
    public R<Map<String, String>> sendEmailCode(@Valid @RequestBody EmailCodeRequest req) {
        userService.sendEmailCode(req.getEmail(), parseScene(req.getScene()));
        return R.ok(Map.of("message", "验证码已发送，请查收邮箱"));
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
    public R<?> changePassword(@RequestBody Map<String, String> body) {
        long userId = StpUtil.getLoginIdAsLong();
        userService.changePassword(userId, body.get("oldPassword"), body.get("newPassword"));
        return R.ok();
    }

    @PostMapping("/forgot-password")
    public R<Map<String, String>> forgotPassword(@Valid @RequestBody EmailOnlyRequest req) {
        userService.forgotPassword(req.getEmail());
        return R.ok(Map.of("message", "如该邮箱已注册，验证码将发送至您的邮箱"));
    }

    @PostMapping("/reset-password")
    public R<?> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        userService.resetPassword(req.getEmail(), req.getEmailCode(), req.getNewPassword());
        return R.ok();
    }

    private EmailCodeScene parseScene(String scene) {
        try {
            return EmailCodeScene.valueOf(scene.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "验证码用途不正确");
        }
    }
}
