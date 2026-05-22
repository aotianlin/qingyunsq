package com.campusforum.post.dto;

import com.campusforum.user.dto.PublicUserVO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CommentVO {

    private Long id;
    private Long postId;
    private Long parentId;
    private Long replyToId;
    private Long authorId;
    private PublicUserVO author;
    private String content;
    private Integer likeCount;
    private List<CommentVO> replies;
    private LocalDateTime createdAt;
}
