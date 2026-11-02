package com.pettown.boot.miniapp;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApifoxConfig {
    @Bean
    public OpenAPI miniappOpenApi() {
        return new OpenAPI().info(new Info()
                .title("PetTown 小程序接口")
                .version("0.0.1"));
    }
}
