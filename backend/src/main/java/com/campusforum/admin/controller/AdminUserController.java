package com.campusforum.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.admin.dto.BatchUpdateUserStatusRequest;
import com.campusforum.admin.dto.ChangeRoleRequest;
import com.campusforum.common.R;
import com.campusforum.infra.audit.AuditLogService;
import com.campusforum.user.dto.UserVO;
import com.campusforum.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 管理后台用户管理接口。
 *
 * <p>对应 bugfix.md 漏洞 17 / T8.7：原来用 {@code Map<String, ...>} 接收 request body，
 * 缺字段 / 类型错误只能跑到 service 才报错（往往以 NullPointerException 或
 * ClassCastException 形式抛出 500），让前端难以定位错误。改造后：</p>
 * <ul>
 *   <li>{@link #changeRole} 改用 {@link ChangeRoleRequest} + {@code @Valid}；</li>
 *   <li>{@link #batchSetStatus} 改用 {@link BatchUpdateUserStatusRequest} + {@code @Valid}；</li>
 *   <li>缺字段 / 字段不合法 → Bean Validation 在 controller 层抛
 *       {@code MethodArgumentNotValidException} → {@code GlobalExceptionHandler}
 *       返回 400 + 字段级错误信息，不再以 500 形式向客户端泄漏内部异常细节。</li>
 * </ul>
 */
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
    public R<Void> changeRole(@PathVariable Long id, @Valid @RequestBody ChangeRoleRequest req) {
        userService.changeRole(id, req.getRole());
        auditLogService.log("USER_ROLE_CHANGE", "user", id,
                "role changed to " + req.getRole() + " by admin " + StpUtil.getLoginIdAsLong());
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
    public R<Void> batchSetStatus(@Valid @RequestBody BatchUpdateUserStatusRequest req) {
        // DTO 上的 @Size(max=100) 已经约束了 ids 上限，无需再次校验
        // 用 LinkedHashSet 去重，避免同一 id 多次 ban/unban 导致重复 kickout
        Set<Long> uniqueIds = new LinkedHashSet<>(req.getIds());
        Long operatorId = StpUtil.getLoginIdAsLong();
        for (Long id : uniqueIds) {
            if (req.getStatus() == 0) userService.banUser(id);
            else userService.unbanUser(id);
        }
        auditLogService.log("USER_BATCH_STATUS", "user", null,
                "batch " + (req.getStatus() == 0 ? "ban" : "unban") + " " + uniqueIds.size() +
                        " users by admin " + operatorId);
        return R.ok();
    }
}
