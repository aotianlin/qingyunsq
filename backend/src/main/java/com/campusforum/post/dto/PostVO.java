package com.campusforum.post.dto;

import com.campusforum.user.dto.PublicUserVO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PostVO {

    private Long id;
    private Long authorId;
    private PublicUserVO author;
    private String scope;
    private Long spaceId;
    private String type;
    private String title;
    private String content;
    private List<String> topics;
    private List<String> tags;
    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private Integer isPinned;
    private Integer isEssence;
    private Integer status;
    private Boolean liked;
    private Boolean collected;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
