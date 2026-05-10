package com.campusforum.checkin.dto;

import lombok.Data;

import java.util.List;

@Data
public class CreateCheckinRecordRequest {
    private String content;
    private List<String> imageUrls;
}
