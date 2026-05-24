package com.campusforum.admin.export;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.infra.audit.AuditLogService;
import com.campusforum.user.domain.User;
import com.campusforum.user.mapper.UserMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/**
 * 后台导出 Controller。
 *
 * <p><b>本次安全加固（任务 T8.6 / 漏洞 13）</b>：</p>
 * <ul>
 *   <li><b>拆分 4 个端点</b>：原先单一 {@code POST /api/v1/admin/export/{dataType}} 用
 *       {@code @SaCheckPermission("tenant:dashboard")} 一刀切，所有进得了后台的角色
 *       都能拉走任意类型的数据。现拆为 4 个独立端点，每个绑定独立的细粒度权限：
 *       <ul>
 *         <li>{@code POST /api/v1/admin/export/users}     → {@code tenant:export:users}</li>
 *         <li>{@code POST /api/v1/admin/export/posts}     → {@code tenant:export:posts}</li>
 *         <li>{@code POST /api/v1/admin/export/audit_logs}→ {@code tenant:export:audit}</li>
 *         <li>{@code POST /api/v1/admin/export/reports}   → {@code tenant:export:reports}</li>
 *       </ul>
 *   </li>
 *   <li><b>fullPii 二次校验</b>：用户/学号导出默认对 PII 做掩码，仅 {@code SUPER_ADMIN}
 *       可显式传 {@code fullPii=true} 拿到原始字段；{@code TENANT_ADMIN} 即便有
 *       {@code tenant:export:users} 也会被这里 {@link ErrorCode#FORBIDDEN} 拦截。
 *       实现上必须在 controller 里做（不能放到 ExportService），因为权限模型需要拿
 *       Sa-Token 登录态判断角色，service 层无 request scope。</li>
 *   <li><b>审计</b>：每次导出写一条 {@code EXPORT_<TYPE>} 审计日志（包含格式 + fullPii 标记）。</li>
 * </ul>
 *
 * <p>响应限流配额由 {@code application.yml} 中 {@code rate-limit.overrides} 统一配置（任务 T5.3）：
 * 每个端点 1/min，使保留接口可用，但攻击者无法持续高频拉取。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;
    private final AuditLogService auditLogService;
    private final UserMapper userMapper;

    private static final Set<String> SUPPORTED_FORMATS = Set.of("csv", "xlsx");

    /**
     * 导出用户。
     *
     * <p>{@code fullPii} 仅 SUPER_ADMIN 可传 {@code true}；其它角色传 {@code true}
     * 会被拒绝（不静默退化为脱敏，避免攻击者反复尝试探测权限边界）。</p>
     */
    @PostMapping("/users")
    @SaCheckPermission("tenant:export:users")
    public void exportUsers(@RequestParam(defaultValue = "csv") String format,
                            @RequestParam(defaultValue = "false") boolean fullPii,
                            HttpServletResponse response) {
        String fmt = validateFormat(format, response);
        if (fmt == null) return;
        boolean effectiveFullPii = enforceFullPiiPermission(fullPii);
        prepareDownloadHeaders(response, "users", fmt);
        try {
            exportService.export("users", fmt, response.getOutputStream(), effectiveFullPii);
        } catch (BusinessException be) {
            // 行数超限等业务异常由全局异常处理器接管，重新抛出
            throw be;
        } catch (Exception e) {
            log.error("Export users failed: {}", e.getMessage());
            throw new RuntimeException("Export failed", e);
        }
        auditLogService.log("EXPORT_USERS", "user", null,
                "format=" + fmt + ",fullPii=" + effectiveFullPii);
    }

    /**
     * 导出帖子。
     */
    @PostMapping("/posts")
    @SaCheckPermission("tenant:export:posts")
    public void exportPosts(@RequestParam(defaultValue = "csv") String format,
                            HttpServletResponse response) {
        String fmt = validateFormat(format, response);
        if (fmt == null) return;
        prepareDownloadHeaders(response, "posts", fmt);
        try {
            exportService.export("posts", fmt, response.getOutputStream(), false);
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("Export posts failed: {}", e.getMessage());
            throw new RuntimeException("Export failed", e);
        }
        auditLogService.log("EXPORT_POSTS", "post", null, "format=" + fmt);
    }

    /**
     * 导出审计日志。
     */
    @PostMapping("/audit_logs")
    @SaCheckPermission("tenant:export:audit")
    public void exportAuditLogs(@RequestParam(defaultValue = "csv") String format,
                                HttpServletResponse response) {
        String fmt = validateFormat(format, response);
        if (fmt == null) return;
        prepareDownloadHeaders(response, "audit_logs", fmt);
        try {
            exportService.export("audit_logs", fmt, response.getOutputStream(), false);
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("Export audit_logs failed: {}", e.getMessage());
            throw new RuntimeException("Export failed", e);
        }
        auditLogService.log("EXPORT_AUDIT_LOGS", "audit_log", null, "format=" + fmt);
    }

    /**
     * 导出举报。
     */
    @PostMapping("/reports")
    @SaCheckPermission("tenant:export:reports")
    public void exportReports(@RequestParam(defaultValue = "csv") String format,
                              HttpServletResponse response) {
        String fmt = validateFormat(format, response);
        if (fmt == null) return;
        prepareDownloadHeaders(response, "reports", fmt);
        try {
            exportService.export("reports", fmt, response.getOutputStream(), false);
        } catch (BusinessException be) {
            throw be;
        } catch (Exception e) {
            log.error("Export reports failed: {}", e.getMessage());
            throw new RuntimeException("Export failed", e);
        }
        auditLogService.log("EXPORT_REPORTS", "report", null, "format=" + fmt);
    }

    /**
     * 校验并归一化 format 参数；非法时直接写 400 并返回 {@code null}。
     */
    private String validateFormat(String format, HttpServletResponse response) {
        String fmt = format == null ? "csv" : format.toLowerCase();
        if (!SUPPORTED_FORMATS.contains(fmt)) {
            response.setStatus(400);
            writeJson(response, "{\"code\":400,\"message\":\"不支持的格式，仅支持 csv 和 xlsx\"}");
            return null;
        }
        return fmt;
    }

    /**
     * fullPii 权限校验（任务 T8.6 / 漏洞 13）。
     *
     * <p>仅当当前登录用户角色为 SUPER_ADMIN 时才允许 {@code fullPii=true}；其它角色
     * 即便传了也会被 {@link ErrorCode#FORBIDDEN} 拒绝（不静默退化为脱敏，留下明确的
     * 4xx 信号方便 SIEM 告警）。</p>
     */
    private boolean enforceFullPiiPermission(boolean requested) {
        if (!requested) return false;
        Long loginId;
        try {
            loginId = StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        User u = userMapper.selectById(loginId);
        if (u == null || !"SUPER_ADMIN".equals(u.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(),
                    "fullPii 仅 SUPER_ADMIN 可用");
        }
        return true;
    }

    private void prepareDownloadHeaders(HttpServletResponse response, String dataType, String format) {
        String fileName = dataType + "_export." + format;
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
        if ("csv".equalsIgnoreCase(format)) {
            response.setContentType("text/csv;charset=UTF-8");
        } else {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        }
    }

    private void writeJson(HttpServletResponse response, String json) {
        response.setContentType("application/json;charset=UTF-8");
        try { response.getWriter().write(json); } catch (Exception ignored) {}
    }
}
