package com.campusforum.checkin.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LeaderboardEntry {
    private Long userId;
    private String userName;
    private String avatarUrl;
    private Integer totalDays;
    private Integer currentStreak;
}
