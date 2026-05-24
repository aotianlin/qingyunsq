package com.campusforum.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.campusforum.admin.dto.AuditLogVO;
import com.campusforum.infra.audit.AuditLogService;
import com.campusforum.common.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    @SaCheckPermission("tenant:audit:log")
    public R<List<AuditLogVO>> list(
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return R.ok(auditLogService.page(cursor, limit, operatorId, action));
    }
}
