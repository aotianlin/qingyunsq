package com.campusforum.message.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campusforum.message.domain.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    @Select("SELECT MAX(id) FROM messages " +
            "WHERE (sender_id = #{userId} OR receiver_id = #{userId}) " +
            "GROUP BY CASE WHEN sender_id = #{userId} THEN receiver_id ELSE sender_id END " +
            "ORDER BY MAX(id) DESC " +
            "LIMIT #{limit}")
    List<Long> selectLatestMessageIdsPerConversation(@Param("userId") Long userId, @Param("limit") int limit);
}
