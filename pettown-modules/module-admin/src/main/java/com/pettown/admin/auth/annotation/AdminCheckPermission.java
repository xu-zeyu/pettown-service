package com.pettown.admin.auth.annotation;

import com.pettown.business.admin.service.AdminPermission;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 管理接口必须声明权限枚举，由 AdminSecurityConfiguration 使用管理员账号空间校验。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminCheckPermission {
    AdminPermission value();
}
