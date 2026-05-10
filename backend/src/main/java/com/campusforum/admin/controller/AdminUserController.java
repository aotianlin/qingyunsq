package com.campusforum.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.admin.service.AuditLogService;
import com.campusforum.common.R;
import com.campusforum.user.dto.UserVO;
import com.campusforum.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;
    private final AuditLogService auditLogService;

    @GetMapping
    @SaCheckPermission("tenant:user:list")
    public R<List<UserVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return R.ok(userService.listUsers(keyword, role, status, cursor, limit));
    }

    @PutMapping("/{id}/role")
    @SaCheckPermission("tenant:user:role")
    public R<Void> changeRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String newRole = body.get("role");
        userService.changeRole(id, newRole);
        auditLogService.log("USER_ROLE_CHANGE", "user", id,
                "role changed to " + newRole + " by admin " + StpUtil.getLoginIdAsLong());
        return R.ok();
    }

    @PutMapping("/{id}/ban")
    @SaCheckPermission("tenant:user:ban")
    public R<?> banUser(@PathVariable Long id) {
        userService.banUser(id);
        auditLogService.log("USER_BAN", "user", id,
                "user banned by admin " + StpUtil.getLoginIdAsLong());
        return R.ok();
    }

    @PutMapping("/{id}/unban")
    @SaCheckPermission("tenant:user:ban")
    public R<?> unbanUser(@PathVariable Long id) {
        userService.unbanUser(id);
        auditLogService.log("USER_UNBAN", "user", id,
                "user unbanned by admin " + StpUtil.getLoginIdAsLong());
        return R.ok();
    }
}
