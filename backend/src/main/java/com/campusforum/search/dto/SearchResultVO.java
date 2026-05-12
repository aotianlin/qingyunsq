package com.campusforum.search.dto;

import com.campusforum.user.dto.UserVO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SearchResultVO {
    private String type;
    private Long id;
    private String title;
    private String description;
    private UserVO author;
    private LocalDateTime createdAt;
    private Integer likeCount;
    private Integer commentCount;
    private Integer viewCount;
    private Integer downloadCount;
    private String category;
    private String fileType;
    private Long fileSize;
    private Integer memberCount;
    private Integer postCount;
}
