package com.campusforum.admin.export;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.admin.domain.AuditLog;
import com.campusforum.admin.mapper.AuditLogMapper;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportService {

    private final UserMapper userMapper;
    private final PostMapper postMapper;
    private final AuditLogMapper auditLogMapper;
    private final ReportMapper reportMapper;

    private static final int BATCH_SIZE = 1000;

    public void export(String dataType, String format, OutputStream out) {
        if ("csv".equals(format)) {
            exportCsv(dataType, out);
        } else {
            exportXlsx(dataType, out);
        }
    }

    private void exportCsv(String dataType, OutputStream out) {
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), true);
        // BOM for Excel compatibility
        writer.print('\uFEFF');

        switch (dataType) {
            case "users" -> exportUsersCsv(writer);
            case "posts" -> exportPostsCsv(writer);
            case "audit_logs" -> exportAuditLogsCsv(writer);
            case "reports" -> exportReportsCsv(writer);
        }
        writer.flush();
    }

    private void exportUsersCsv(PrintWriter writer) {
        writer.println("id,email,nickname,student_no,college,major,grade,role,points,status,created_at");
        Long lastId = null;
        while (true) {
            LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
            if (lastId != null) qw.gt(User::getId, lastId);
            qw.orderByAsc(User::getId).last("LIMIT " + BATCH_SIZE);
            List<User> batch = userMapper.selectList(qw);
            if (batch.isEmpty()) break;
            for (User u : batch) {
                writer.printf("%d,%s,%s,%s,%s,%s,%s,%s,%d,%d,%s%n",
                        u.getId(), esc(u.getEmail()), esc(u.getNickname()), esc(u.getStudentNo()),
                        esc(u.getCollege()), esc(u.getMajor()), esc(u.getGrade()),
                        u.getRole(), u.getPoints(), u.getStatus(), u.getCreatedAt());
            }
            lastId = batch.get(batch.size() - 1).getId();
        }
    }

    private void exportPostsCsv(PrintWriter writer) {
        writer.println("id,author_id,scope,space_id,type,title,view_count,like_count,comment_count,status,created_at");
        Long lastId = null;
        while (true) {
            LambdaQueryWrapper<Post> qw = new LambdaQueryWrapper<>();
            if (lastId != null) qw.gt(Post::getId, lastId);
            qw.orderByAsc(Post::getId).last("LIMIT " + BATCH_SIZE);
            List<Post> batch = postMapper.selectList(qw);
            if (batch.isEmpty()) break;
            for (Post p : batch) {
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
        while (true) {
            LambdaQueryWrapper<AuditLog> qw = new LambdaQueryWrapper<>();
            if (lastId != null) qw.gt(AuditLog::getId, lastId);
            qw.orderByAsc(AuditLog::getId).last("LIMIT " + BATCH_SIZE);
            List<AuditLog> batch = auditLogMapper.selectList(qw);
            if (batch.isEmpty()) break;
            for (AuditLog a : batch) {
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
        while (true) {
            LambdaQueryWrapper<Report> qw = new LambdaQueryWrapper<>();
            if (lastId != null) qw.gt(Report::getId, lastId);
            qw.orderByAsc(Report::getId).last("LIMIT " + BATCH_SIZE);
            List<Report> batch = reportMapper.selectList(qw);
            if (batch.isEmpty()) break;
            for (Report r : batch) {
                writer.printf("%d,%d,%s,%d,%s,%d,%s,%s%n",
                        r.getId(), r.getReporterId(), r.getTargetType(), r.getTargetId(),
                        esc(r.getReason()), r.getStatus(), r.getCreatedAt(),
                        r.getHandledAt() != null ? r.getHandledAt() : "");
            }
            lastId = batch.get(batch.size() - 1).getId();
        }
    }

    private void exportXlsx(String dataType, OutputStream out) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet(dataType);
            switch (dataType) {
                case "users" -> exportUsersXlsx(sheet);
                case "posts" -> exportPostsXlsx(sheet);
                case "audit_logs" -> exportAuditLogsXlsx(sheet);
                case "reports" -> exportReportsXlsx(sheet);
            }
            workbook.write(out);
        } catch (Exception e) {
            log.error("XLSX export failed: {}", e.getMessage());
            throw new RuntimeException("Export failed", e);
        }
    }

    private void exportUsersXlsx(Sheet sheet) {
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
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(u.getId());
                row.createCell(1).setCellValue(str(u.getEmail()));
                row.createCell(2).setCellValue(str(u.getNickname()));
                row.createCell(3).setCellValue(str(u.getStudentNo()));
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
        // 简化：仅写 header，实际数据按同样模式分批写入
        int rowNum = 1;
        Long lastId = null;
        while (true) {
            LambdaQueryWrapper<Post> qw = new LambdaQueryWrapper<>();
            if (lastId != null) qw.gt(Post::getId, lastId);
            qw.orderByAsc(Post::getId).last("LIMIT " + BATCH_SIZE);
            List<Post> batch = postMapper.selectList(qw);
            if (batch.isEmpty()) break;
            for (Post p : batch) {
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

    private String esc(String val) {
        if (val == null) return "";
        return val.replace(",", "，").replace("\n", " ").replace("\r", "");
    }

    private String str(String val) {
        return val != null ? val : "";
    }
}
