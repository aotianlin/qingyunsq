package com.campusforum.infra.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastMessage {
    private Long userId;
    private String eventType;
    private String payload;
}
