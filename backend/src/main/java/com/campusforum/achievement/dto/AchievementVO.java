package com.campusforum.achievement.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AchievementVO {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String iconUrl;
    private boolean awarded;
    private LocalDateTime awardedAt;
}
