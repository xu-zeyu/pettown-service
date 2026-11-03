package com.pettown.admin.config;

import cn.dev33.satoken.stp.StpInterface;
import com.pettown.admin.interceptor.AdminAuthorizationInterceptor;
import com.pettown.business.admin.entity.AdminRole;
import com.pettown.business.admin.service.AdminService;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class AdminSecurityConfiguration implements WebMvcConfigurer {
    private final AdminService service;

    public AdminSecurityConfiguration(AdminService service) {
        this.service = service;
    }

    @Bean
    public StpInterface adminPermissionProvider() {
        return new StpInterface() {
            @Override
            public List<String> getPermissionList(Object loginId, String loginType) {
                return "admin".equals(loginType)
                        ? service.permissions(Long.valueOf(loginId.toString())).stream().sorted().toList() : List.of();
            }

            @Override
            public List<String> getRoleList(Object loginId, String loginType) {
                return "admin".equals(loginType)
                        ? service.roles(Long.valueOf(loginId.toString())).stream().map(AdminRole::getCode).toList() : List.of();
            }
        };
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminAuthorizationInterceptor(service))
                .addPathPatterns("/**")
                .excludePathPatterns("/error", "/v3/api-docs", "/v3/api-docs/**");
    }
}
