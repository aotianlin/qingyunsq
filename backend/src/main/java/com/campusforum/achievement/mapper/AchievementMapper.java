package com.campusforum.achievement.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campusforum.achievement.domain.Achievement;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AchievementMapper extends BaseMapper<Achievement> {
}
