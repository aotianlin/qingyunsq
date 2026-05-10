package com.campusforum.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AuditLogVO {
    private Long id;
    private Long operatorId;
    private String operatorName;
    private String action;
    private String targetType;
    private Long targetId;
    private String detail;
    private String ipAddress;
    private LocalDateTime createdAt;
}
