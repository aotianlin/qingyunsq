package com.campusforum.checkin.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("checkin_records")
public class CheckinRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long challengeId;
    private Long userId;
    private LocalDate checkinDate;
    private String content;
    private String imageUrls;
    private Integer aiCheck;
    private LocalDateTime createdAt;
}
