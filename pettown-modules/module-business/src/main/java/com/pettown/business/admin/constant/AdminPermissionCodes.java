package com.pettown.business.admin.constant;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 管理后台内置权限编码。
 * 与 {@code docs/sql/admin.sql} 的内置权限目录一一对应，供 {@code @SaCheckPermission} 引用编译期常量。
 * 额外的前端权限点由 {@code admin_permission} 目录维护。
 */
public final class AdminPermissionCodes {

    private AdminPermissionCodes() {
    }

    /** 查看管理员 */
    public static final String ACCOUNT_READ = "system:account:read";
    /** 创建管理员 */
    public static final String ACCOUNT_CREATE = "system:account:create";
    /** 修改管理员 */
    public static final String ACCOUNT_UPDATE = "system:account:update";
    /** 启停管理员 */
    public static final String ACCOUNT_STATUS = "system:account:status";
    /** 重置管理员密码 */
    public static final String ACCOUNT_RESET_PASSWORD = "system:account:reset-password";
    /** 分配管理员角色 */
    public static final String ACCOUNT_ASSIGN_ROLE = "system:account:assign-role";
    /** 删除管理员 */
    public static final String ACCOUNT_DELETE = "system:account:delete";
    /** 查看角色 */
    public static final String ROLE_READ = "system:role:read";
    /** 创建角色 */
    public static final String ROLE_CREATE = "system:role:create";
    /** 修改角色 */
    public static final String ROLE_UPDATE = "system:role:update";
    /** 删除角色 */
    public static final String ROLE_DELETE = "system:role:delete";
    /** 分配角色权限 */
    public static final String ROLE_ASSIGN_PERMISSION = "system:role:assign-permission";
    /** 查看权限点 */
    public static final String PERMISSION_READ = "system:permission:read";
    /** 查看 C 端用户 */
    public static final String USER_READ = "biz:user:read";
    /** 封禁或解封 C 端用户 */
    public static final String USER_STATUS = "biz:user:status";
    /** 管理后台权限与子管理员 */
    public static final String SUB_ADMIN = "SUB_ADMIN";

    /** 内置编码不可改名、不可删除。 */
    public static Set<String> builtinCodes() {
        return Set.copyOf(new LinkedHashSet<>(Set.of(
                ACCOUNT_READ, ACCOUNT_CREATE, ACCOUNT_UPDATE, ACCOUNT_STATUS,
                ACCOUNT_RESET_PASSWORD, ACCOUNT_ASSIGN_ROLE, ACCOUNT_DELETE,
                ROLE_READ, ROLE_CREATE, ROLE_UPDATE, ROLE_DELETE, ROLE_ASSIGN_PERMISSION,
                PERMISSION_READ, USER_READ, USER_STATUS, SUB_ADMIN)));
    }
}
