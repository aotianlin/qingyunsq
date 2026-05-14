package com.campusforum.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campusforum.message.domain.Message;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {
}
