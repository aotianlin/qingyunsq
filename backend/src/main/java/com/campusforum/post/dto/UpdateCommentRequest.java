package com.campusforum.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateCommentRequest {

    @NotBlank
    @Size(min = 1, max = 5000)
    private String content;
}
