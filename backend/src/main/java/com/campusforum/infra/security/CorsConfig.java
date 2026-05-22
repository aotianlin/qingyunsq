package com.campusforum.infra.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * CORS 显式配置（缺陷 1.30）。
 *
 * <p>原本后端没有显式配置 CORS，依赖 Spring 默认拒绝跨域，但生产环境如果运维在 nginx 层
 * 误配置 {@code Access-Control-Allow-Origin: *} + {@code Allow-Credentials: true} 会形成漏洞。
 * 本类显式声明白名单，使配置位置明确并可审计。</p>
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>{@code allowedOrigins} 来自 {@link SecurityProperties.Cors#getAllowedOrigins()}，
 *       生产环境必须通过 ENV {@code CORS_ALLOWED_ORIGINS} 显式配置；</li>
 *   <li>{@code allowCredentials = false}：本项目使用 {@code Authorization} 头携带 Sa-Token，
 *       不依赖 cookie，关闭凭证可避免 origin 通配带来的潜在风险；</li>
 *   <li>仅对 {@code /api/**} 路径生效；WebSocket 由 {@code WebSocketConfig.setAllowedOrigins} 单独控制。</li>
 * </ul>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CorsConfig implements WebMvcConfigurer {

    private final SecurityProperties securityProperties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = securityProperties.getCors().getAllowedOrigins();
        if (origins == null || origins.isEmpty()) {
            log.warn("CorsConfig: cors.allowed-origins 未配置，跨域请求将被默认拒绝");
            return;
        }
        List<String> methods = securityProperties.getCors().getAllowedMethods();
        registry.addMapping("/api/**")
                .allowedOrigins(origins.toArray(new String[0]))
                .allowedMethods(methods.toArray(new String[0]))
                .allowedHeaders("*")
                .exposedHeaders("Retry-After")
                .allowCredentials(false)
                .maxAge(3600);
        log.info("CorsConfig: registered {} allowed origins", origins.size());
    }
}
