package com.campusforum.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campusforum.user.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Update("UPDATE user SET points = points + #{amount} WHERE id = #{userId}")
    int incrementPoints(@Param("userId") Long userId, @Param("amount") long amount);

    @Update("UPDATE user SET points = points - #{amount} WHERE id = #{userId} AND points >= #{amount}")
    int decrementPoints(@Param("userId") Long userId, @Param("amount") long amount);

    @Select("<script>" +
            "SELECT id FROM user WHERE tag_subscriptions IS NOT NULL " +
            "AND tag_subscriptions != '' AND tag_subscriptions != '[]' " +
            "AND (" +
            "<foreach collection='tags' item='tag' separator=' OR '>" +
            "tag_subscriptions LIKE CONCAT('%\"', #{tag}, '\"%')" +
            "</foreach>" +
            ")" +
            "</script>")
    List<Long> selectUserIdsByTagSubscription(@Param("tags") List<String> tags);
}
