package com.campusforum.security;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 仅对 /api/v1/** 应用 Sa-Token 强制登录拦截，且仅放行匿名访问的认证类接口。
        // 注意：actuator / swagger / api-docs 不再列入 excludePathPatterns，
        // 它们由 Spring Security 之外的网关或 management.endpoints.web.exposure.include 进一步限制。
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        "/api/v1/auth/email-code",
                        "/api/v1/auth/email-exists",
                        "/api/v1/auth/forgot-password",
                        "/api/v1/auth/reset-password",
                        // 资源下载/预览支持签名 URL 模式（&lt;a&gt; 直链等场景），controller 内部会按 sig 或登录态进行校验
                        "/api/v1/resources/*/download",
                        "/api/v1/resources/*/preview"
                );
    }
}
