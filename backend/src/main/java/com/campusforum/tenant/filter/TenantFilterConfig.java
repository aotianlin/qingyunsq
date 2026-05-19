package com.campusforum.tenant.filter;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 注册 TenantResolutionFilter 为 Servlet Filter，
 * order 设为 HIGHEST_PRECEDENCE + 50，确保在 CORS/Security 之后、Sa-Token 之前执行。
 */
@Configuration
public class TenantFilterConfig {

    @Bean
    public FilterRegistrationBean<TenantResolutionFilter> tenantResolutionFilterRegistration(
            TenantResolutionFilter filter) {
        FilterRegistrationBean<TenantResolutionFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 50);
        registration.setName("tenantResolutionFilter");
        return registration;
    }
}
