package com.campusforum.infra;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI campusForumOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("CampusForum API")
                        .description("基于多租户架构与 AI 增强的高校轻量化学习社群平台")
                        .version("1.0.0")
                        .contact(new Contact().name("CampusForum").url("https://github.com/zhh123465/CampusForum"))
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")));
    }

    @Bean
    public GroupedOpenApi v1Api() {
        return GroupedOpenApi.builder()
                .group("v1")
                .displayName("API v1")
                .pathsToMatch("/api/v1/**")
                .build();
    }
}
