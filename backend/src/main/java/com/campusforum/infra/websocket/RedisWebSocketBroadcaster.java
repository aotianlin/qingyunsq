package com.campusforum.infra.websocket;

import com.campusforum.notify.websocket.SessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisWebSocketBroadcaster implements WebSocketBroadcaster, MessageListener {

    private final StringRedisTemplate redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;
    private final SessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    private static final String CHANNEL = "campusforum:ws:broadcast";

    @PostConstruct
    public void init() {
        listenerContainer.addMessageListener(this, new ChannelTopic(CHANNEL));
        log.info("WebSocket broadcaster subscribed to Redis channel: {}", CHANNEL);
    }

    @Override
    public void broadcast(BroadcastMessage message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            if (json.length() > 65536) {
                log.warn("Broadcast message exceeds 64KB limit, dropping");
                return;
            }
            redisTemplate.convertAndSend(CHANNEL, json);
        } catch (Exception e) {
            // Redis 不可用时降级为本地投递
            log.warn("Redis broadcast failed, falling back to local delivery: {}", e.getMessage());
            deliverLocally(message);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody());
            BroadcastMessage msg = objectMapper.readValue(json, BroadcastMessage.class);
            deliverLocally(msg);
        } catch (Exception e) {
            log.error("Failed to process broadcast message: {}", e.getMessage());
        }
    }

    private void deliverLocally(BroadcastMessage msg) {
        if (msg.getUserId() == null) return;
        sessionRegistry.sendToUser(msg.getUserId(), msg.getPayload());
    }
}
