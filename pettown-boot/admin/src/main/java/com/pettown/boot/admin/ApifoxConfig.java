package com.pettown.boot.admin;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ApifoxConfig {
    @Bean
    public OpenAPI adminOpenApi() {
        return new OpenAPI().info(new Info()
                .title("PetTown 管理后台接口")
                .version("0.0.1")
                .description("管理后台认证、管理员与角色权限接口；统一使用 Result<T> 响应"))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .description("管理员登录后返回的 Token，请通过 Authorization: Bearer <token> 请求头传递")));
    }
}
