package com.campusforum;

import com.campusforum.tenant.TenantProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(TenantProperties.class)
public class CampusForumApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusForumApplication.class, args);
    }
}
