package com.campusforum.admin.export;

import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;
    private static final Set<String> SUPPORTED_TYPES = Set.of("users", "posts", "audit_logs", "reports");
    private static final Set<String> SUPPORTED_FORMATS = Set.of("csv", "xlsx");

    @PostMapping("/{dataType}")
    @SaCheckPermission("tenant:dashboard")
    public void export(@PathVariable String dataType,
                       @RequestParam(defaultValue = "csv") String format,
                       HttpServletResponse response) {
        if (!SUPPORTED_TYPES.contains(dataType)) {
            response.setStatus(400);
            writeJson(response, "{\"code\":400,\"message\":\"不支持的数据类型: " + dataType + "\"}");
            return;
        }
        if (!SUPPORTED_FORMATS.contains(format.toLowerCase())) {
            response.setStatus(400);
            writeJson(response, "{\"code\":400,\"message\":\"不支持的格式，仅支持 csv 和 xlsx\"}");
            return;
        }

        String fileName = dataType + "_export." + format.toLowerCase();
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName);

        if ("csv".equalsIgnoreCase(format)) {
            response.setContentType("text/csv;charset=UTF-8");
        } else {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        }

        try {
            exportService.export(dataType, format.toLowerCase(), response.getOutputStream());
        } catch (Exception e) {
            throw new RuntimeException("Export failed", e);
        }
    }

    private void writeJson(HttpServletResponse response, String json) {
        response.setContentType("application/json;charset=UTF-8");
        try { response.getWriter().write(json); } catch (Exception ignored) {}
    }
}
