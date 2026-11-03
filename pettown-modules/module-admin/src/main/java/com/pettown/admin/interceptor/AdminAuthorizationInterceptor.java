package com.pettown.admin.interceptor;

import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.router.SaRouter;
import com.pettown.admin.auth.annotation.AdminCheckPermission;
import com.pettown.admin.auth.controller.AdminAuthController;
import com.pettown.admin.auth.controller.AdminLoginController;
import com.pettown.business.admin.service.AdminService;
import com.pettown.common.security.config.PetTownToken;
import com.pettown.common.security.interceptor.SaTokenDispatchInterceptor;
import java.util.Set;
import org.springframework.web.method.HandlerMethod;

/** 管理员账号状态、会话版本和接口权限校验，不进入公共安全模块。 */
public class AdminAuthorizationInterceptor extends SaTokenDispatchInterceptor {
    public AdminAuthorizationInterceptor(AdminService service) {
        super(handler -> {
            SaRouter.match("/**")
                    .notMatch("/api/admin/auth/verification")
                    .check(r -> PetTownToken.ADMIN.checkLogin());
            authorize(service, handler);
        });
    }

    private static void authorize(AdminService service, Object handler) {
        PetTownToken.ADMIN.checkLogin();
        if (!(handler instanceof HandlerMethod method)) {
            return;
        }
        var account = service.findAccount(PetTownToken.ADMIN.getLoginIdAsLong()).orElse(null);
        var session = PetTownToken.ADMIN.getTokenSession(false);
        if (account == null || !Boolean.TRUE.equals(account.getEnabled()) || session == null
                || !String.valueOf(account.getSessionVersion()).equals(
                        String.valueOf(session.get(AdminAuthController.SESSION_VERSION)))) {
            PetTownToken.ADMIN.logout();
            PetTownToken.ADMIN.checkLogin();
        }
        boolean authController = method.getBeanType().equals(AdminAuthController.class);
        boolean loginController = method.getBeanType().equals(AdminLoginController.class);
        String name = method.getMethod().getName();
        boolean selfService = (authController && Set.of("me", "logout", "changePassword").contains(name))
                || (loginController && Set.of("self", "info").contains(name));
        if (account != null && Boolean.TRUE.equals(account.getPasswordChangeRequired()) && !selfService) {
            throw new NotPermissionException("请先修改初始密码", "admin");
        }
        if (selfService || (authController && name.equals("menus"))) {
            return;
        }
        AdminCheckPermission permission = method.getMethodAnnotation(AdminCheckPermission.class);
        if (permission == null) {
            throw new NotPermissionException("接口未声明管理权限", "admin");
        }
        PetTownToken.ADMIN.checkPermission(permission.value().code());
    }
}
