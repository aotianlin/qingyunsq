package com.campusforum.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.common.R;
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
    public R<?> changePassword(@RequestBody Map<String, String> body) {
        long userId = StpUtil.getLoginIdAsLong();
        userService.changePassword(userId, body.get("oldPassword"), body.get("newPassword"));
        return R.ok();
    }
}
