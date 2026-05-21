package com.campusforum.infra.websocket;

import com.campusforum.notify.websocket.SessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 基于 Redis pub/sub 的 WebSocket 集群广播实现。
 * 当 RedisMessageListenerContainer 不可用时（如测试环境），降级为本地投递。
 */
@Slf4j
@Component
public class RedisWebSocketBroadcaster implements WebSocketBroadcaster, MessageListener {

    private final StringRedisTemplate redisTemplate;
    private final SessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;
    private final RedisMessageListenerContainer listenerContainer;

    private static final String CHANNEL = "campusforum:ws:broadcast";

    @Autowired
    public RedisWebSocketBroadcaster(StringRedisTemplate redisTemplate,
                                     SessionRegistry sessionRegistry,
                                     ObjectMapper objectMapper,
                                     @Autowired(required = false) RedisMessageListenerContainer listenerContainer) {
        this.redisTemplate = redisTemplate;
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
        this.listenerContainer = listenerContainer;
    }

    @PostConstruct
    public void init() {
        if (listenerContainer != null) {
            listenerContainer.addMessageListener(this, new ChannelTopic(CHANNEL));
            log.info("WebSocket broadcaster subscribed to Redis channel: {}", CHANNEL);
        } else {
            log.warn("RedisMessageListenerContainer not available, WebSocket broadcast in local-only mode");
        }
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
