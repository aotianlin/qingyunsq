package com.campusforum.checkin.dto;

import com.campusforum.user.dto.PublicUserVO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class CheckinChallengeVO {
    private Long id;
    private Long spaceId;
    private Long creatorId;
    private PublicUserVO creator;
    private String name;
    private String description;
    private LocalDate startDate;
    private LocalDate endDate;
    private String rule;
    private Integer memberCount;
    private Integer status;
    private Boolean isMember;
    private Integer myTotalDays;
    private Integer myConsecutiveDays;
    private LocalDateTime createdAt;
}
