package com.pettown.business.admin.service;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** 权限点与接口一起维护，客户端不能创建通配权限。 */
public enum AdminPermission {
    ACCOUNT_READ("system:account:read", "查看管理员"),
    ACCOUNT_CREATE("system:account:create", "创建管理员"),
    ACCOUNT_UPDATE("system:account:update", "修改管理员"),
    ACCOUNT_STATUS("system:account:status", "启停管理员"),
    ACCOUNT_RESET_PASSWORD("system:account:reset-password", "重置管理员密码"),
    ACCOUNT_ASSIGN_ROLE("system:account:assign-role", "分配管理员角色"),
    ROLE_READ("system:role:read", "查看角色"),
    ROLE_CREATE("system:role:create", "创建角色"),
    ROLE_UPDATE("system:role:update", "修改角色"),
    ROLE_DELETE("system:role:delete", "删除角色"),
    ROLE_ASSIGN_PERMISSION("system:role:assign-permission", "分配角色权限"),
    PERMISSION_READ("system:permission:read", "查看权限点");

    private final String code;
    private final String title;

    AdminPermission(String code, String title) {
        this.code = code;
        this.title = title;
    }

    public String code() { return code; }
    public String title() { return title; }

    public static Set<String> codes() {
        return Arrays.stream(values()).map(AdminPermission::code).collect(Collectors.toUnmodifiableSet());
    }
}
