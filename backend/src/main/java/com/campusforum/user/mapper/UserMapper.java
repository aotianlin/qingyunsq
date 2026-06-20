package com.campusforum.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campusforum.user.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("<script>" +
            "SELECT id FROM users WHERE tenant_id = #{tenantId} " +
            "AND tag_subscriptions IS NOT NULL " +
            "AND tag_subscriptions != '' AND tag_subscriptions != '[]' " +
            "AND (" +
            "<foreach collection='tags' item='tag' separator=' OR '>" +
            "tag_subscriptions LIKE CONCAT('%\"', #{tag}, '\"%') ESCAPE '\\\\'" +
            "</foreach>" +
            ")" +
            "</script>")
    List<Long> selectUserIdsByTagSubscription(
            @Param("tenantId") Long tenantId,
            @Param("tags") List<String> tags);
}
