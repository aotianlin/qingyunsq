package com.campusforum.infra;

import com.campusforum.infra.ratelimit.RateLimitInterceptor;
import com.campusforum.tenant.interceptor.TenantBindingCheckInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置。
 *
 * <p>原 TenantInterceptor 已由 TenantResolutionFilter（Servlet Filter）替代，
 * 不再在此注册。TenantBindingCheckInterceptor 用于已认证请求的租户绑定校验。</p>
 *
 * <p>安全说明：原先存在的 {@code /uploads/**} 静态资源映射已删除。
 * 该映射会让本地存储目录的所有文件不经鉴权即可访问，
 * 现在所有资源访问必须经过 ResourceController 的可见性校验或签名 URL。</p>
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantBindingCheckInterceptor tenantBindingCheckInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 限流拦截器（优先级最高）
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/v1/**")
                .order(0);

        registry.addInterceptor(tenantBindingCheckInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns("/api/v1/auth/**")
                .order(1);
    }
}
