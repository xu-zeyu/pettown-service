package com.pettown.boot.admin.interceptor;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.exception.NotPermissionException;
import com.pettown.admin.auth.controller.AdminAuthController;
import com.pettown.business.admin.service.AdminService;
import com.pettown.common.security.config.PetTownToken;
import com.pettown.common.security.interceptor.SaTokenDispatchInterceptor;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.springframework.web.method.HandlerMethod;

/**
 * 管理员会话校验：在 Sa-Token 注解校验之前执行。
 * 逐请求重读账号状态与会话版本，禁用、删除、改密、换手机号或重置密码后的旧 Token 一律失效；
 * 首次改密前仅放行个人信息、改密与退出接口。方法上的 {@link SaIgnore} 显式公开，不按路径猜测放行。
 */
public class AdminSessionInterceptor extends SaTokenDispatchInterceptor {

    /** 首次改密前仍可访问的个人信息/改密/退出路径。 */
    private static final Set<String> PASSWORD_CHANGE_EXEMPT_PATHS = Set.of(
            "/mng/auth/me", "/mng/auth/logout", "/mng/auth/password", "/api/admin/auth/me",
            "/api/admin/auth/change-password");

    private final AdminService adminService;

    public AdminSessionInterceptor(AdminService adminService) {
        super(handler -> {
        });
        this.adminService = adminService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        DispatcherType dispatcherType = request.getDispatcherType();
        if (dispatcherType == DispatcherType.ASYNC || dispatcherType == DispatcherType.ERROR) {
            return true;
        }
        if (!(handler instanceof HandlerMethod method) || !isPublic(method)) {
            checkSession(request);
        }
        // 注解权限校验（@SaCheckPermission）由父类在会话校验之后执行。
        return super.preHandle(request, response, handler);
    }

    private void checkSession(HttpServletRequest request) {
        PetTownToken.ADMIN.checkLogin();
        Long accountId = PetTownToken.ADMIN.getLoginIdAsLong();
        var account = adminService.findAccount(accountId).orElse(null);
        var session = PetTownToken.ADMIN.getTokenSession(false);
        boolean sessionStale = account == null
                || !Boolean.TRUE.equals(account.getEnabled())
                || session == null
                || !String.valueOf(account.getSessionVersion()).equals(
                        String.valueOf(session.get(AdminAuthController.SESSION_VERSION)));
        if (sessionStale) {
            // 账号被删除/禁用或会话版本变化：注销当前 Token 并要求重新登录。
            PetTownToken.ADMIN.logout();
            PetTownToken.ADMIN.checkLogin();
        }
        if (Boolean.TRUE.equals(account.getPasswordChangeRequired())
                && !PASSWORD_CHANGE_EXEMPT_PATHS.contains(request.getRequestURI())) {
            throw new NotPermissionException("请先修改初始密码", "admin");
        }
    }

    /** 仅方法或类上显式标注 {@link SaIgnore} 的接口公开。 */
    private boolean isPublic(HandlerMethod method) {
        return method.hasMethodAnnotation(SaIgnore.class)
                || method.getBeanType().isAnnotationPresent(SaIgnore.class);
    }
}
