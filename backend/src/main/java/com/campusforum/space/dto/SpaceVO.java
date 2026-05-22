package com.campusforum.space.dto;

import com.campusforum.user.dto.PublicUserVO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SpaceVO {

    private Long id;
    private Long ownerId;
    private PublicUserVO owner;
    private String name;
    private String description;
    private String category;
    private String visibility;
    private Integer memberCount;
    private Integer postCount;
    private Integer status;
    private Boolean isMember;
    private String memberRole;
    private String sensitiveWords;
    private String postNotice;
    private LocalDateTime createdAt;
}
