package com.campusforum.checkin.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("checkin_challenges")
public class CheckinChallenge {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long spaceId;
    private Long creatorId;
    private String name;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
    private String rule;
    private Integer memberCount;
    private Integer status;
    private LocalDateTime createdAt;
}
