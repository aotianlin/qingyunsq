package com.campusforum.admin.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计日志实体（表 audit_logs）。
 *
 * <p>对应迁移：</p>
 * <ul>
 *   <li>原始表见 {@code db/schema.sql}；</li>
 *   <li>{@code user_agent} 列与 {@code idx_audit_log_action_created} 索引由
 *       {@code db/migrations/V20260601_04__audit_log_extend.sql} 增量加上
 *       （T9.2，对应 bugfix.md 漏洞 26 异步审计上下文）。</li>
 * </ul>
 */
@Data
@TableName("audit_logs")
public class AuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long operatorId;
    private String action;
    private String targetType;
    private Long targetId;
    private String detail;
    private String ipAddress;
    /**
     * 客户端 User-Agent。
     *
     * <p>新增于 V20260601_04 迁移，最大 255 字符；超长部分由
     * {@link com.campusforum.infra.audit.AuditLogService} 在写库前主动截断，
     * 避免触发 SQL 字段截断告警 / 严格模式下报错。</p>
     */
    private String userAgent;
    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;
}
