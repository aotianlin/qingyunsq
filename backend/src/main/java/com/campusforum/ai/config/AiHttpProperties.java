package com.campusforum.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * AI 上游 HTTP 客户端超时配置。
 *
 * <p>绑定 application.yml 中的 ai.http 节，由 TenantAwareAiService 注入并传给 OpenAiCompatService。
 * 配置变更需要重启后端生效（不在 AiClientKey 缓存键中，所以不会触发 cache miss 自动 reload）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.http")
public class AiHttpProperties {

    /** 建立 HTTP 连接的超时时间 */
    private Duration connectTimeout = Duration.ofSeconds(8);

    /** 从 LLM 读取完整响应的超时时间（流式响应未启用时使用） */
    private Duration readTimeout = Duration.ofSeconds(30);
}
