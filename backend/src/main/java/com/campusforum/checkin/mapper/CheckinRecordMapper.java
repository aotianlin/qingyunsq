package com.campusforum.checkin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campusforum.checkin.domain.CheckinRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CheckinRecordMapper extends BaseMapper<CheckinRecord> {
}
