package com.pettown.admin.auth;

import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.stp.StpInterface;
import com.pettown.admin.auth.annotation.AdminCheckPermission;
import com.pettown.admin.auth.controller.AdminAuthController;
import com.pettown.admin.auth.controller.AdminLoginController;
import com.pettown.business.admin.service.AdminService;
import com.pettown.common.security.config.PetTownToken;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
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
                        ? service.roles(Long.valueOf(loginId.toString())).stream().map(role -> role.getCode()).toList() : List.of();
            }
        };
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (!(handler instanceof HandlerMethod method)) {
                    return true;
                }
                // 只放行真正的登录方法；其他新增接口默认要求登录及明确的权限声明。
                boolean authController = method.getBeanType().equals(AdminAuthController.class);
                boolean loginController = method.getBeanType().equals(AdminLoginController.class);
                String name = method.getMethod().getName();
                if ((authController && name.equals("login")) || (loginController
                        && Set.of("smsLogin", "createLoginCode").contains(name))) {
                    return true;
                }
                PetTownToken.ADMIN.checkLogin();
                var account = service.findAccount(PetTownToken.ADMIN.getLoginIdAsLong()).orElse(null);
                var session = PetTownToken.ADMIN.getTokenSession(false);
                if (account == null || !Boolean.TRUE.equals(account.getEnabled()) || session == null
                        || !String.valueOf(account.getSessionVersion()).equals(
                                String.valueOf(session.get(AdminAuthController.SESSION_VERSION)))) {
                    PetTownToken.ADMIN.logout();
                    PetTownToken.ADMIN.checkLogin();
                }
                boolean selfService = (authController && Set.of("me", "logout", "changePassword").contains(name))
                        || (loginController && Set.of("self", "info").contains(name));
                if (Boolean.TRUE.equals(account.getPasswordChangeRequired()) && !selfService) {
                    throw new NotPermissionException("请先修改初始密码", "admin");
                }
                if (selfService || (authController && name.equals("menus"))) {
                    return true;
                }
                AdminCheckPermission permission = method.getMethodAnnotation(AdminCheckPermission.class);
                if (permission == null) {
                    throw new NotPermissionException("接口未声明管理权限", "admin");
                }
                PetTownToken.ADMIN.checkPermission(permission.value().code());
                return true;
            }
        }).addPathPatterns("/api/admin/**");
    }
}
