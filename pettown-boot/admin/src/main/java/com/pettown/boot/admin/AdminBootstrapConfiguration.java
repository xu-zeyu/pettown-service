package com.pettown.boot.admin;

import com.pettown.business.admin.service.AdminService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AdminBootstrapConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "pettown.admin.bootstrap", name = "enabled", havingValue = "true")
    public ApplicationRunner initializeAdmin(AdminService service,
            @Value("${pettown.admin.bootstrap.username:}") String username,
            @Value("${pettown.admin.bootstrap.password:}") String password,
            @Value("${pettown.admin.bootstrap.phone:}") String phone) {
        return args -> service.initializeSuperAdmin(username, password, phone);
    }
}
