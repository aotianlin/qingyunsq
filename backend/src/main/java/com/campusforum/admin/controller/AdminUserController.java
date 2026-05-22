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

    @PutMapping("/batch-status")
    @SaCheckPermission("tenant:user:ban")
    public R<Void> batchSetStatus(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> ids = ((List<Number>) body.get("ids")).stream()
                .map(Number::longValue).toList();
        // 安全加固（缺陷 1.16）：批量操作硬上限，避免攻击者传入数万 ID 造成 N+1 长事务
        if (ids.size() > 100) {
            throw new com.campusforum.common.BusinessException(
                    com.campusforum.common.ErrorCode.BATCH_SIZE_EXCEEDED);
        }
        int status = Integer.parseInt(body.get("status").toString());
        Long operatorId = StpUtil.getLoginIdAsLong();
        for (Long id : ids) {
            if (status == 0) userService.banUser(id);
            else userService.unbanUser(id);
        }
        auditLogService.log("USER_BATCH_STATUS", "user", null,
                "batch " + (status == 0 ? "ban" : "unban") + " " + ids.size() +
                        " users by admin " + operatorId);
        return R.ok();
    }
}
