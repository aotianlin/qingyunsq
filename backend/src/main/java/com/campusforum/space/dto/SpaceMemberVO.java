package com.campusforum.space.dto;

import com.campusforum.user.dto.PublicUserVO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SpaceMemberVO {

    private Long id;
    private Long spaceId;
    private Long userId;
    private PublicUserVO user;
    private String role;
    private Integer status;
    private LocalDateTime joinedAt;
}
