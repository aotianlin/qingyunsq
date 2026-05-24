package com.campusforum.admin.export;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.admin.domain.AuditLog;
import com.campusforum.admin.mapper.AuditLogMapper;
import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.post.domain.Post;
import com.campusforum.post.mapper.PostMapper;
import com.campusforum.report.domain.Report;
import com.campusforum.report.mapper.ReportMapper;
import com.campusforum.user.domain.User;
import com.campusforum.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 后台导出服务。
 *
 * <p><b>本次安全加固（任务 T8.6 / 漏洞 13）</b>：</p>
 * <ul>
 *   <li><b>PII 脱敏</b>：默认所有 export 调用都对 {@code email} / {@code studentNo} 做掩码，
 *       仅 SUPER_ADMIN 显式传 {@code fullPii=true} 才能拿到原始字段。租户管理员
 *       即便有 {@code tenant:export:users} 权限也无法越权读到完整 PII。</li>
 *   <li><b>MAX_ROWS 上限</b>：单次导出最多 {@value #MAX_ROWS} 行，超限抛
 *       {@link ErrorCode#BATCH_SIZE_EXCEEDED}，避免攻击者用导出端点把整库刷下来。</li>
 *   <li><b>掩码规则</b>：
 *       <ul>
 *         <li>email：保留首字符 + {@code ***} + {@code @后缀}（例如 {@code a***@example.edu}）；</li>
 *         <li>studentNo：保留前 4 + {@code ***} + 末 1（例如 {@code 2023***5}）。</li>
 *       </ul>
 *   </li>
 *   <li><b>CSV 公式注入防御</b>：保留原有 {@code esc/str} 对以 {@code = + - @ Tab} 开头
 *       的字段加单引号转义。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private final UserMapper userMapper;
    private final PostMapper postMapper;
    private final AuditLogMapper auditLogMapper;
    private final ReportMapper reportMapper;

    /**
     * 单批查询大小：用于游标分页拉数据。
     */
    private static final int BATCH_SIZE = 1000;

    /**
     * 单次导出最大行数（漏洞 13 / 任务 T8.6）。
     *
     * <p>超过此阈值会抛 {@link ErrorCode#BATCH_SIZE_EXCEEDED}，作为对"刷库式导出"的硬上限。
     * 真实运营如需全量导出请走数据库直连或专门 ETL 通道，不该走 admin 接口。</p>
     */
    static final int MAX_ROWS = 50_000;

    /**
     * 兼容旧入口：默认按"已脱敏"导出。
     *
     * <p>新代码请使用 {@link #export(String, String, OutputStream, boolean)}，明确表达
     * 是否需要完整 PII 字段。</p>
     */
    public void export(String dataType, String format, OutputStream out) {
        export(dataType, format, out, false);
    }

    /**
     * 主入口：按 dataType / format 导出，支持 fullPii 开关。
     *
     * @param dataType {@code users} / {@code posts} / {@code audit_logs} / {@code reports}
     * @param format   {@code csv} / {@code xlsx}
     * @param out      响应输出流（由 controller 提供）
     * @param fullPii  是否输出完整 PII 字段（仅 SUPER_ADMIN 可传 {@code true}，由 controller 校验）
     */
    public void export(String dataType, String format, OutputStream out, boolean fullPii) {
        if ("csv".equals(format)) {
            exportCsv(dataType, out, fullPii);
        } else {
            exportXlsx(dataType, out, fullPii);
        }
    }

    private void exportCsv(String dataType, OutputStream out, boolean fullPii) {
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), true);
        // BOM for Excel compatibility
        writer.print('\uFEFF');

        switch (dataType) {
            case "users" -> exportUsersCsv(writer, fullPii);
            case "posts" -> exportPostsCsv(writer);
            case "audit_logs" -> exportAuditLogsCsv(writer);
            case "reports" -> exportReportsCsv(writer);
        }
        writer.flush();
    }

    private void exportUsersCsv(PrintWriter writer, boolean fullPii) {
        writer.println("id,email,nickname,student_no,college,major,grade,role,points,status,created_at");
        Long lastId = null;
        // 已写入数据行计数（不含表头），命中 MAX_ROWS 时抛错
        int written = 0;
        while (true) {
            LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
            if (lastId != null) qw.gt(User::getId, lastId);
            qw.orderByAsc(User::getId).last("LIMIT " + BATCH_SIZE);
            List<User> batch = userMapper.selectList(qw);
            if (batch.isEmpty()) break;
            for (User u : batch) {
                checkRowLimit(++written);
                String email = fullPii ? u.getEmail() : maskEmail(u.getEmail());
                String studentNo = fullPii ? u.getStudentNo() : maskStudentNo(u.getStudentNo());
                writer.printf("%d,%s,%s,%s,%s,%s,%s,%s,%d,%d,%s%n",
                        u.getId(), esc(email), esc(u.getNickname()), esc(studentNo),
                        esc(u.getCollege()), esc(u.getMajor()), esc(u.getGrade()),
                        u.getRole(), u.getPoints(), u.getStatus(), u.getCreatedAt());
            }
            lastId = batch.get(batch.size() - 1).getId();
        }
    }

    private void exportPostsCsv(PrintWriter writer) {
        writer.println("id,author_id,scope,space_id,type,title,view_count,like_count,comment_count,status,created_at");
        Long lastId = null;
        int written = 0;
        while (true) {
            LambdaQueryWrapper<Post> qw = new LambdaQueryWrapper<>();
            if (lastId != null) qw.gt(Post::getId, lastId);
            qw.orderByAsc(Post::getId).last("LIMIT " + BATCH_SIZE);
            List<Post> batch = postMapper.selectList(qw);
            if (batch.isEmpty()) break;
            for (Post p : batch) {
                checkRowLimit(++written);
                writer.printf("%d,%d,%s,%s,%s,%s,%d,%d,%d,%d,%s%n",
                        p.getId(), p.getAuthorId(), p.getScope(),
                        p.getSpaceId() != null ? p.getSpaceId() : "",
                        p.getType(), esc(p.getTitle()),
                        p.getViewCount(), p.getLikeCount(), p.getCommentCount(),
                        p.getStatus(), p.getCreatedAt());
            }
            lastId = batch.get(batch.size() - 1).getId();
        }
    }

    private void exportAuditLogsCsv(PrintWriter writer) {
        writer.println("id,operator_id,action,target_type,target_id,ip_address,created_at");
        Long lastId = null;
        int written = 0;
        while (true) {
            LambdaQueryWrapper<AuditLog> qw = new LambdaQueryWrapper<>();
            if (lastId != null) qw.gt(AuditLog::getId, lastId);
            qw.orderByAsc(AuditLog::getId).last("LIMIT " + BATCH_SIZE);
            List<AuditLog> batch = auditLogMapper.selectList(qw);
            if (batch.isEmpty()) break;
            for (AuditLog a : batch) {
                checkRowLimit(++written);
                writer.printf("%d,%s,%s,%s,%s,%s,%s%n",
                        a.getId(), a.getOperatorId() != null ? a.getOperatorId() : "",
                        esc(a.getAction()), esc(a.getTargetType()),
                        a.getTargetId() != null ? a.getTargetId() : "",
                        esc(a.getIpAddress()), a.getCreatedAt());
            }
            lastId = batch.get(batch.size() - 1).getId();
        }
    }

    private void exportReportsCsv(PrintWriter writer) {
        writer.println("id,reporter_id,target_type,target_id,reason,status,created_at,handled_at");
        Long lastId = null;
        int written = 0;
        while (true) {
            LambdaQueryWrapper<Report> qw = new LambdaQueryWrapper<>();
            if (lastId != null) qw.gt(Report::getId, lastId);
            qw.orderByAsc(Report::getId).last("LIMIT " + BATCH_SIZE);
            List<Report> batch = reportMapper.selectList(qw);
            if (batch.isEmpty()) break;
            for (Report r : batch) {
                checkRowLimit(++written);
                writer.printf("%d,%d,%s,%d,%s,%d,%s,%s%n",
                        r.getId(), r.getReporterId(), r.getTargetType(), r.getTargetId(),
                        esc(r.getReason()), r.getStatus(), r.getCreatedAt(),
                        r.getHandledAt() != null ? r.getHandledAt() : "");
            }
            lastId = batch.get(batch.size() - 1).getId();
        }
    }

    private void exportXlsx(String dataType, OutputStream out, boolean fullPii) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet(dataType);
            switch (dataType) {
                case "users" -> exportUsersXlsx(sheet, fullPii);
                case "posts" -> exportPostsXlsx(sheet);
                case "audit_logs" -> exportAuditLogsXlsx(sheet);
                case "reports" -> exportReportsXlsx(sheet);
            }
            workbook.write(out);
        } catch (BusinessException be) {
            // 行数超限属于业务异常，应原样抛给全局异常处理器返回 BATCH_SIZE_EXCEEDED
            throw be;
        } catch (Exception e) {
            log.error("XLSX export failed: {}", e.getMessage());
            throw new RuntimeException("Export failed", e);
        }
    }

    private void exportUsersXlsx(Sheet sheet, boolean fullPii) {
        Row header = sheet.createRow(0);
        String[] cols = {"id", "email", "nickname", "student_no", "college", "major", "grade", "role", "points", "status", "created_at"};
        for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);

        int rowNum = 1;
        Long lastId = null;
        while (true) {
            LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
            if (lastId != null) qw.gt(User::getId, lastId);
            qw.orderByAsc(User::getId).last("LIMIT " + BATCH_SIZE);
            List<User> batch = userMapper.selectList(qw);
            if (batch.isEmpty()) break;
            for (User u : batch) {
                checkRowLimit(rowNum); // 不含表头：rowNum 从 1 起
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(u.getId());
                row.createCell(1).setCellValue(str(fullPii ? u.getEmail() : maskEmail(u.getEmail())));
                row.createCell(2).setCellValue(str(u.getNickname()));
                row.createCell(3).setCellValue(str(fullPii ? u.getStudentNo() : maskStudentNo(u.getStudentNo())));
                row.createCell(4).setCellValue(str(u.getCollege()));
                row.createCell(5).setCellValue(str(u.getMajor()));
                row.createCell(6).setCellValue(str(u.getGrade()));
                row.createCell(7).setCellValue(str(u.getRole()));
                row.createCell(8).setCellValue(u.getPoints());
                row.createCell(9).setCellValue(u.getStatus());
                row.createCell(10).setCellValue(u.getCreatedAt() != null ? u.getCreatedAt().toString() : "");
            }
            lastId = batch.get(batch.size() - 1).getId();
        }
    }

    private void exportPostsXlsx(Sheet sheet) {
        Row header = sheet.createRow(0);
        String[] cols = {"id", "author_id", "scope", "space_id", "type", "title", "view_count", "like_count", "comment_count", "status", "created_at"};
        for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);
        int rowNum = 1;
        Long lastId = null;
        while (true) {
            LambdaQueryWrapper<Post> qw = new LambdaQueryWrapper<>();
            if (lastId != null) qw.gt(Post::getId, lastId);
            qw.orderByAsc(Post::getId).last("LIMIT " + BATCH_SIZE);
            List<Post> batch = postMapper.selectList(qw);
            if (batch.isEmpty()) break;
            for (Post p : batch) {
                checkRowLimit(rowNum);
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(p.getId());
                row.createCell(1).setCellValue(p.getAuthorId());
                row.createCell(2).setCellValue(str(p.getScope()));
                row.createCell(3).setCellValue(p.getSpaceId() != null ? p.getSpaceId() : 0);
                row.createCell(4).setCellValue(str(p.getType()));
                row.createCell(5).setCellValue(str(p.getTitle()));
                row.createCell(6).setCellValue(p.getViewCount());
                row.createCell(7).setCellValue(p.getLikeCount());
                row.createCell(8).setCellValue(p.getCommentCount());
                row.createCell(9).setCellValue(p.getStatus());
                row.createCell(10).setCellValue(p.getCreatedAt() != null ? p.getCreatedAt().toString() : "");
            }
            lastId = batch.get(batch.size() - 1).getId();
        }
    }

    private void exportAuditLogsXlsx(Sheet sheet) {
        Row header = sheet.createRow(0);
        String[] cols = {"id", "operator_id", "action", "target_type", "target_id", "ip_address", "created_at"};
        for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);
    }

    private void exportReportsXlsx(Sheet sheet) {
        Row header = sheet.createRow(0);
        String[] cols = {"id", "reporter_id", "target_type", "target_id", "reason", "status", "created_at", "handled_at"};
        for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);
    }

    /**
     * 行数上限校验（任务 T8.6 / 漏洞 13）。
     *
     * <p>超过 {@link #MAX_ROWS} 时抛 {@link ErrorCode#BATCH_SIZE_EXCEEDED}，
     * 由 {@code GlobalExceptionHandler} 统一翻译为 4xx 响应。</p>
     */
    private void checkRowLimit(int writtenRows) {
        if (writtenRows > MAX_ROWS) {
            throw new BusinessException(ErrorCode.BATCH_SIZE_EXCEEDED.getCode(),
                    "导出行数超过上限 " + MAX_ROWS + "，请缩小筛选范围或走专用 ETL 通道");
        }
    }

    /**
     * 邮箱掩码：保留首字符 + {@code ***} + {@code @后缀}（任务 T8.6 / 漏洞 13）。
     *
     * <p>规则：</p>
     * <ul>
     *   <li>{@code null} / 空 → 返回原值；</li>
     *   <li>无 {@code @} → 返回 {@code ***}（视为脏数据，不透露任何字符）；</li>
     *   <li>{@code a@b.com} → {@code a***@b.com}；</li>
     *   <li>{@code abc@b.com} → {@code a***@b.com}（始终保留首位）。</li>
     * </ul>
     */
    static String maskEmail(String email) {
        if (email == null || email.isEmpty()) return email;
        int at = email.indexOf('@');
        if (at < 0) return "***";
        String suffix = email.substring(at);
        if (at == 0) return "***" + suffix;
        return email.charAt(0) + "***" + suffix;
    }

    /**
     * 学号掩码：保留前 4 + {@code ***} + 末 1（任务 T8.6 / 漏洞 13）。
     *
     * <p>规则：</p>
     * <ul>
     *   <li>{@code null} / 空 → 返回原值；</li>
     *   <li>长度 ≤ 5：保留首位 + {@code ***}（不暴露中间，仍能辨别是否为同一系列）；</li>
     *   <li>{@code 20239876} → {@code 2023***6}。</li>
     * </ul>
     */
    static String maskStudentNo(String studentNo) {
        if (studentNo == null || studentNo.isEmpty()) return studentNo;
        if (studentNo.length() <= 5) {
            return studentNo.charAt(0) + "***";
        }
        return studentNo.substring(0, 4) + "***" + studentNo.charAt(studentNo.length() - 1);
    }

    private String esc(String val) {
        if (val == null) return "";
        String safe = val.replace(",", "，").replace("\n", " ").replace("\r", "");
        // CSV 公式注入防御：以 = + - @ Tab 开头的内容会被 Excel 当公式解析，前置单引号转义
        if (!safe.isEmpty()) {
            char first = safe.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t') {
                safe = "'" + safe;
            }
        }
        return safe;
    }

    private String str(String val) {
        if (val == null) return "";
        if (!val.isEmpty()) {
            char first = val.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t') {
                return "'" + val;
            }
        }
        return val;
    }
}
