package com.campusforum.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateCommentRequest {

    @NotNull
    private Long postId;

    private Long parentId;
    private Long replyToId;

    @NotBlank(message = "评论内容不能为空")
    @Size(min = 1, max = 5000, message = "评论内容不能超过 5000 字")
    private String content;
}
