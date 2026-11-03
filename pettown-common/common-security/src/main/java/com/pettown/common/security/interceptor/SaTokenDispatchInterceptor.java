package com.pettown.common.security.interceptor;

import cn.dev33.satoken.fun.SaParamFunction;
import cn.dev33.satoken.interceptor.SaInterceptor;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 初次请求执行注解和路由鉴权，异步及错误内部分发不重复鉴权。 */
public class SaTokenDispatchInterceptor extends SaInterceptor {
    public SaTokenDispatchInterceptor(SaParamFunction<Object> auth) {
        super(auth);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        DispatcherType dispatcherType = request.getDispatcherType();
        if (dispatcherType == DispatcherType.ASYNC || dispatcherType == DispatcherType.ERROR) {
            return true;
        }
        return super.preHandle(request, response, handler);
    }
}
