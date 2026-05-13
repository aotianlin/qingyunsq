package com.campusforum.sensitive.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campusforum.sensitive.domain.SensitiveWord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SensitiveWordMapper extends BaseMapper<SensitiveWord> {
}
