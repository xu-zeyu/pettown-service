package com.pettown.common.security.config;

import cn.dev33.satoken.filter.SaTokenContextFilterForJakartaServlet;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** 只提供 Servlet 上下文装配；客户端自行决定账号空间和接口放行规则。 */
@Configuration(proxyBeanMethods = false)
public class SaTokenWebConfiguration {
    @Bean
    public FilterRegistrationBean<SaTokenContextFilterForJakartaServlet> saTokenContextFilterRegistration(
            SaTokenContextFilterForJakartaServlet filter) {
        FilterRegistrationBean<SaTokenContextFilterForJakartaServlet> registration =
                new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setAsyncSupported(true);
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC);
        return registration;
    }
}
