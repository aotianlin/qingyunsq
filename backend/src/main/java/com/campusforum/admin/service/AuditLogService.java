package com.campusforum.admin.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.admin.domain.AuditLog;
import com.campusforum.admin.dto.AuditLogVO;
import com.campusforum.admin.mapper.AuditLogMapper;
import com.campusforum.infra.security.TrustedProxyResolver;
import com.campusforum.user.domain.User;
import com.campusforum.user.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;
    private final UserMapper userMapper;
    private final HttpServletRequest request;
    private final TrustedProxyResolver trustedProxyResolver;

    @Transactional
    public void log(String action, String targetType, Long targetId, String detail) {
        AuditLog entry = new AuditLog();
        entry.setOperatorId(StpUtil.getLoginIdAsLong());
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setDetail(detail);
        entry.setIpAddress(getClientIp());

        auditLogMapper.insert(entry);
        log.debug("Audit log recorded: action={}, operator={}", action, entry.getOperatorId());
    }

    public List<AuditLogVO> page(Long cursor, int limit, Long operatorId, String action) {
        int size = Math.min(limit, 50);
        LambdaQueryWrapper<AuditLog> qw = new LambdaQueryWrapper<>();
        if (cursor != null) {
            qw.lt(AuditLog::getId, cursor);
        }
        if (operatorId != null) {
            qw.eq(AuditLog::getOperatorId, operatorId);
        }
        if (action != null && !action.isBlank()) {
            qw.eq(AuditLog::getAction, action);
        }
        qw.orderByDesc(AuditLog::getId);
        qw.last("LIMIT " + size);

        return auditLogMapper.selectList(qw).stream().map(this::toVO).toList();
    }

    private AuditLogVO toVO(AuditLog log) {
        String operatorName = null;
        if (log.getOperatorId() != null) {
            User operator = userMapper.selectById(log.getOperatorId());
            if (operator != null) {
                operatorName = operator.getNickname();
            }
        }

        return AuditLogVO.builder()
                .id(log.getId())
                .operatorId(log.getOperatorId())
                .operatorName(operatorName)
                .action(log.getAction())
                .targetType(log.getTargetType())
                .targetId(log.getTargetId())
                .detail(log.getDetail())
                .ipAddress(log.getIpAddress())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private String getClientIp() {
        // 安全加固（缺陷 1.17）：通过 TrustedProxyResolver 获取真实 IP，
        // 仅在请求来源为可信代理时才接受 X-Forwarded-For，与限流器逻辑保持一致。
        // 否则攻击者可伪造 X-Forwarded-For 在审计日志中栽赃他人 IP。
        return trustedProxyResolver.resolve(request);
    }
}
