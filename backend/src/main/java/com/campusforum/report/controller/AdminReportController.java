package com.campusforum.report.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.campusforum.common.R;
import com.campusforum.report.dto.ReportVO;
import com.campusforum.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportService reportService;

    @GetMapping
    @SaCheckPermission("tenant:report:manage")
    public R<List<ReportVO>> list(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Integer status) {
        return R.ok(reportService.page(cursor, limit, targetType, status));
    }

    @PutMapping("/{id}/handle")
    @SaCheckPermission("tenant:report:manage")
    public R<Void> handle(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Integer status = Integer.valueOf(body.get("status").toString());
        String note = (String) body.getOrDefault("note", null);
        reportService.handle(id, status, note);
        return R.ok();
    }

    @PutMapping("/batch-handle")
    @SaCheckPermission("tenant:report:manage")
    public R<Void> batchHandle(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> ids = ((List<Number>) body.get("ids")).stream()
                .map(Number::longValue).toList();
        Integer status = Integer.valueOf(body.get("status").toString());
        String note = (String) body.getOrDefault("note", "");
        for (Long id : ids) {
            reportService.handle(id, status, note);
        }
        return R.ok();
    }
}
