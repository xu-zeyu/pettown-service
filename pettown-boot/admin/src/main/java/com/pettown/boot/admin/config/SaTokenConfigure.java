package com.pettown.boot.admin.config;

import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import com.pettown.common.security.interceptor.SaTokenDispatchInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 类名: SaTokenConfigure [Sa-Token 权限认证] 配置类
 * 描述: 注册全局过滤器，使用独立的 Admin StpLogic
 * 作者: xuzeyu
 * 创建时间: 2026/1/1
 */
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaTokenDispatchInterceptor(handler -> {
            // 登录校验。ASYNC 和 ERROR 内部分发由拦截器直接跳过，不再重复鉴权。
            SaRouter
                    .match("/**")
                    .notMatch("/login/sms", "/public/**", "/error", "/v3/api-docs", "/v3/api-docs/**")
                    .check(r -> StpUtil.checkLogin());
        })).addPathPatterns("/**");
    }
}
