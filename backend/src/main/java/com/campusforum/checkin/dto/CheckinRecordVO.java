package com.campusforum.checkin.dto;

import com.campusforum.user.dto.PublicUserVO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CheckinRecordVO {
    private Long id;
    private Long challengeId;
    private Long userId;
    private PublicUserVO user;
    private LocalDate checkinDate;
    private String content;
    private List<String> imageUrls;
    private Integer aiCheck;
    private LocalDateTime createdAt;
}
