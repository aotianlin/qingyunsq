package com.campusforum.resource.dto;

import com.campusforum.user.dto.PublicUserVO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ResourceVO {
    private Long id;
    private Long uploaderId;
    private PublicUserVO uploader;
    private Long spaceId;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private String visibility;
    private String college;
    private String major;
    private String course;
    private String semester;
    private List<String> tags;
    private Integer downloadCount;
    private Integer collectCount;
    private String version;
    private String description;
    private LocalDateTime createdAt;
}
