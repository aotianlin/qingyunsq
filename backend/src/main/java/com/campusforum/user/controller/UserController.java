package com.campusforum.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.common.R;
import com.campusforum.user.dto.UpdateProfileRequest;
import com.campusforum.user.dto.UserVO;
import com.campusforum.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public R<UserVO> getMe() {
        long userId = StpUtil.getLoginIdAsLong();
        return R.ok(userService.getById(userId));
    }

    @PutMapping("/me")
    public R<UserVO> updateMe(@Valid @RequestBody UpdateProfileRequest req) {
        long userId = StpUtil.getLoginIdAsLong();
        return R.ok(userService.updateProfile(userId, req));
    }

    @GetMapping("/me/mute-settings")
    public R<Set<String>> getMuteSettings() {
        long userId = StpUtil.getLoginIdAsLong();
        return R.ok(userService.getMuteSettings(userId));
    }

    @PutMapping("/me/mute-settings")
    public R<?> updateMuteSettings(@RequestBody Map<String, Set<String>> body) {
        long userId = StpUtil.getLoginIdAsLong();
        userService.updateMuteSettings(userId, body.getOrDefault("mutedTypes", Set.of()));
        return R.ok();
    }

    @GetMapping("/me/tag-subscriptions")
    public R<Set<String>> getTagSubscriptions() {
        long userId = StpUtil.getLoginIdAsLong();
        return R.ok(userService.getTagSubscriptions(userId));
    }

    @PutMapping("/me/tag-subscriptions")
    public R<?> updateTagSubscriptions(@RequestBody Map<String, Set<String>> body) {
        long userId = StpUtil.getLoginIdAsLong();
        userService.updateTagSubscriptions(userId, body.getOrDefault("tags", Set.of()));
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<UserVO> getById(@PathVariable Long id) {
        return R.ok(userService.getById(id));
    }
}
