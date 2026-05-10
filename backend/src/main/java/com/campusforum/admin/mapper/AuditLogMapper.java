package com.campusforum.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campusforum.admin.domain.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
