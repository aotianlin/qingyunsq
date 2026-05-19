package com.campusforum.infra;

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
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantBindingCheckInterceptor tenantBindingCheckInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantBindingCheckInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns("/api/v1/auth/**");
    }
}
