package com.campusforum.ai.service;

/**
 * AI 客户端缓存 key — record 自动提供 equals/hashCode，避免 Objects.hash 碰撞风险。
 * 仅在 JVM 堆内驻留（Caffeine 内存缓存，不持久化），apiKey 不会泄露到磁盘或日志。
 */
record AiClientKey(Long tenantId, String baseUrl, String apiKey, String model) {}
