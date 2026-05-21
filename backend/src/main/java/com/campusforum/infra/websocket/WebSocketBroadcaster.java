package com.campusforum.infra.websocket;

/**
 * WebSocket 消息广播接口，支持集群部署
 */
public interface WebSocketBroadcaster {

    /**
     * 广播消息到所有实例
     */
    void broadcast(BroadcastMessage message);
}
