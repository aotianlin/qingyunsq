package com.campusforum.tenant;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 租户配置属性，绑定 application.yml 中 tenant.* 前缀。
 *
 * <p>standalone 模式：所有请求使用固定 standaloneTenantId，适用于单校部署。</p>
 * <p>multi 模式：通过子域名或 X-Tenant-Id 识别租户，适用于 SaaS 多校部署。</p>
 */
@Data
@ConfigurationProperties(prefix = "tenant")
public class TenantProperties {

    /** standalone | multi */
    private TenantMode mode = TenantMode.STANDALONE;

    /** standalone 模式下使用的固定租户 id */
    private long standaloneTenantId = 1L;

    /** multi 模式下用于子域名识别的根域名（如 "campusforum.com"），
     *  Host 形如 "{code}.campusforum.com" 时识别为 code */
    private String rootDomain;

    /** 是否在 multi 模式下接受 X-Tenant-Id 作为 auth 接口的回退来源 */
    private boolean allowHeaderFallback = true;

    /** Active 租户缓存配置 */
    private Cache cache = new Cache();

    @Data
    public static class Cache {
        /** 缓存最大条目数 */
        private int maxSize = 1024;
        /** 缓存过期时间（写入后） */
        private Duration ttl = Duration.ofSeconds(60);
    }
}
