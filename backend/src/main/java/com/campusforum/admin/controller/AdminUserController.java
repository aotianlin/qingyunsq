package com.campusforum.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.campusforum.common.R;
import com.campusforum.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    @PutMapping("/{id}/ban")
    @SaCheckPermission("tenant:user:ban")
    public R<?> banUser(@PathVariable Long id) {
        userService.banUser(id);
        return R.ok();
    }

    @PutMapping("/{id}/unban")
    @SaCheckPermission("tenant:user:ban")
    public R<?> unbanUser(@PathVariable Long id) {
        userService.unbanUser(id);
        return R.ok();
    }
}
