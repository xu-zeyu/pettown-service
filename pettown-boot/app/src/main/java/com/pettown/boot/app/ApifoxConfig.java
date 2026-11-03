package com.pettown.boot.app;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ApifoxConfig {
    @Bean
    public OpenAPI appOpenApi() {
        return new OpenAPI().info(new Info()
                .title("PetTown App 接口")
                .version("0.0.1")
                .description("App 宠物接口；统一使用 Result<T> 响应，全部需要用户登录"))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .description("与小程序共享用户账号空间，通过 Authorization: Bearer <token> 请求头传递用户 Token")));
    }
}
