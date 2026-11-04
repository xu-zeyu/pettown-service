package com.pettown.business.admin.service;

import com.pettown.business.admin.entity.AdminRole;
import java.util.List;
import java.util.Set;

/**
 * 管理后台业务门面：聚合账号、认证、角色、权限域的对外契约，
 * 供控制器、启动装配与 Sa-Token 权限提供者统一使用；实现只做委托，不承载业务规则。
 */
public interface AdminService extends AdminAccountService, AdminAuthService, AdminRoleService, AdminPermissionService {

    /** 账号当前有效权限编码；超级管理员为完整目录，响应中始终返回具体编码。 */
    Set<String> permissions(Long accountId);

    /** 账号关联的已启用角色；单角色模型下最多一项。 */
    List<AdminRole> roles(Long accountId);

    /** 账号关联的角色 ID；未关联时为空数组。 */
    List<Long> accountRoleIds(Long accountId);

    /** 批量同步的权限定义。 */
    record PermissionDefinition(String name, String code) {
    }
}
