package com.campusforum.resource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campusforum.resource.domain.Resource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ResourceMapper extends BaseMapper<Resource> {

    /** 原子自增下载计数，避免并发下载丢失更新。 */
    @Update("UPDATE resources SET download_count = download_count + 1 WHERE id = #{id}")
    int incrementDownloadCount(@Param("id") Long id);
}
