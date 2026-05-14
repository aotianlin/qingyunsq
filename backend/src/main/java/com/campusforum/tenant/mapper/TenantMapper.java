package com.campusforum.tenant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campusforum.tenant.domain.Tenant;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TenantMapper extends BaseMapper<Tenant> {
}
