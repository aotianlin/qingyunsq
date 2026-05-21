package com.campusforum.space.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campusforum.space.domain.Space;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SpaceMapper extends BaseMapper<Space> {

    @Update("UPDATE space SET member_count = member_count + #{delta} WHERE id = #{spaceId}")
    int incrementMemberCount(@Param("spaceId") Long spaceId, @Param("delta") int delta);
}
