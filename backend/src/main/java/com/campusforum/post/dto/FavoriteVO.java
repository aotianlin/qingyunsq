package com.campusforum.post.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class FavoriteVO {

    private Long id;           // reaction ID (用作游标)
    private String targetType; // POST / RESOURCE
    private Long targetId;
    private LocalDateTime collectedAt;

    // 帖子摘要（targetType=POST 时填充）
    private String postTitle;
    private String postContentPreview;

    // 资源摘要（targetType=RESOURCE 时填充）
    private String resourceFileName;
    private String resourceFileType;
}
