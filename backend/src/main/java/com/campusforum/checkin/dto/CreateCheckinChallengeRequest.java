package com.campusforum.checkin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateCheckinChallengeRequest {
    @NotBlank
    @Size(max = 64)
    private String name;

    @Size(max = 500)
    private String description;

    private Long spaceId;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    private String rule;
}
