package com.campusforum.admin.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardVO {
    private Long userCount;
    private Long postCount;
    private Long spaceCount;
    private Long commentCount;
    private Long todayPostCount;
    private Long todayUserCount;
}
