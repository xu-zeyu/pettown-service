package com.pettown.boot.app.config;

import com.pettown.common.security.config.PetTownToken;
import com.pettown.common.security.interceptor.SaTokenDispatchInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class AppSecurityConfiguration implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaTokenDispatchInterceptor(handler -> PetTownToken.USER.checkLogin()))
                .addPathPatterns("/**")
                .excludePathPatterns("/error", "/v3/api-docs", "/v3/api-docs/**");
    }
}
