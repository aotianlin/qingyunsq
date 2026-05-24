package com.campusforum.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.infra.audit.AuditLogService;
import com.campusforum.common.R;
import com.campusforum.space.dto.SpaceVO;
import com.campusforum.space.service.SpaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/spaces")
@RequiredArgsConstructor
public class AdminSpaceController {

    private final SpaceService spaceService;
    private final AuditLogService auditLogService;

    @GetMapping
    @SaCheckPermission("tenant:space:manage")
    public R<List<SpaceVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return R.ok(spaceService.listSpacesForAdmin(keyword, category, status, cursor, limit));
    }

    @PutMapping("/{id}/status")
    @SaCheckPermission("tenant:space:manage")
    public R<Void> setStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Integer newStatus = body.get("status");
        spaceService.setStatus(id, newStatus);
        auditLogService.log("SPACE_STATUS", "space", id,
                "space status set to " + newStatus + " by admin " + StpUtil.getLoginIdAsLong());
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("tenant:space:manage")
    public R<Void> dismiss(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        spaceService.dismiss(id, userId);
        auditLogService.log("SPACE_DISMISS", "space", id,
                "space dismissed by admin " + StpUtil.getLoginIdAsLong());
        return R.ok();
    }
}
