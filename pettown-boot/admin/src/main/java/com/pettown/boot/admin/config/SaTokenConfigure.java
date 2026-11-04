package com.pettown.boot.admin.config;

import com.pettown.boot.admin.interceptor.AdminSessionInterceptor;
import com.pettown.business.admin.service.AdminService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 类名: SaTokenConfigure [Sa-Token 权限认证] 配置类
 * 描述: 注册管理员会话拦截器；注解权限由 Sa-Token 按 admin 账号空间校验
 * 作者: xuzeyu
 * 创建时间: 2026/1/1
 */
@Configuration(proxyBeanMethods = false)
public class SaTokenConfigure implements WebMvcConfigurer {

    private final AdminService adminService;

    public SaTokenConfigure(AdminService adminService) {
        this.adminService = adminService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // ASYNC 和 ERROR 内部分发由拦截器跳过，不重复鉴权；公开接口仅认方法上的 @SaIgnore。
        registry.addInterceptor(new AdminSessionInterceptor(adminService))
                .addPathPatterns("/**")
                .excludePathPatterns("/error", "/v3/api-docs", "/v3/api-docs/**");
    }
}
