package com.pettown.boot.miniapp.config;

import com.pettown.business.user.mapper.UserMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class MiniappSecurityConfiguration implements WebMvcConfigurer {
    private final UserMapper users;

    public MiniappSecurityConfiguration(UserMapper users) {
        this.users = users;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new MiniappUserSessionInterceptor(users))
                .addPathPatterns("/**")
                .excludePathPatterns("/error", "/v3/api-docs", "/v3/api-docs/**");
    }
}
