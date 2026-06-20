package com.campusforum.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreatePostRequest {

    @NotBlank
    private String scope = "SQUARE";

    private Long spaceId;
    private String type = "NORMAL";

    @Size(max = 255)
    private String title;

    @NotBlank
    @Size(min = 1, max = 20000)
    private String content;

    private List<String> topics;
    private List<String> tags;
    private Long quotePostId;
}
