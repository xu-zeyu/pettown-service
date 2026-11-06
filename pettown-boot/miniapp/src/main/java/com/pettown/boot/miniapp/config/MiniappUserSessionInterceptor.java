package com.pettown.boot.miniapp.config;

import cn.dev33.satoken.annotation.SaIgnore;
import com.pettown.business.user.entity.UserStatus;
import com.pettown.business.user.mapper.UserMapper;
import com.pettown.common.security.config.PetTownToken;
import com.pettown.common.security.interceptor.SaTokenDispatchInterceptor;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;

/** 逐请求核验 USER Token 对应账号，封禁后即使遇到并发登录也不能继续访问。 */
public class MiniappUserSessionInterceptor extends SaTokenDispatchInterceptor {
    private final UserMapper users;

    public MiniappUserSessionInterceptor(UserMapper users) {
        super(handler -> PetTownToken.USER.checkLogin());
        this.users = users;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (request.getDispatcherType() == DispatcherType.ASYNC
                || request.getDispatcherType() == DispatcherType.ERROR) {
            return true;
        }
        boolean allowed = super.preHandle(request, response, handler);
        if (allowed && handler instanceof HandlerMethod method
                && !method.hasMethodAnnotation(SaIgnore.class)
                && !method.getBeanType().isAnnotationPresent(SaIgnore.class)) {
            var user = users.selectById(PetTownToken.USER.getLoginIdAsString());
            if (user == null || user.getStatus() != UserStatus.ACTIVE) {
                PetTownToken.USER.logout();
                PetTownToken.USER.checkLogin();
            }
        }
        return allowed;
    }
}
