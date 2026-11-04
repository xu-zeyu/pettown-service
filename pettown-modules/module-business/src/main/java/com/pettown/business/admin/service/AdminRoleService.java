package com.pettown.business.admin.service;

import com.pettown.business.admin.entity.AdminPermissionRecord;
import com.pettown.business.admin.entity.AdminRole;
import com.pettown.common.mybatis.PageParam;
import com.pettown.common.mybatis.PageResult;
import java.util.List;

/** 管理员角色：两套命名风格的角色维护、删除与权限绑定。 */
public interface AdminRoleService {

    /** 旧角色接口：按编码/名称模糊查询。 */
    PageResult<AdminRole> listRoles(PageParam page, String keyword, Boolean enabled);

    /** 参考角色接口：按创建时间倒序。 */
    PageResult<AdminRole> listNamedRoles(PageParam page);

    AdminRole getRole(Long id);

    /** 参考角色接口创建角色：以 name 创建，名称不可与未删除角色重复。 */
    AdminRole createNamedRole(Long actorId, String name, String description);

    /** 旧角色接口更新：修改名称和启用状态；禁用后角色权限立即失效。 */
    void updateRole(Long actorId, Long id, String name, boolean enabled);

    /** 参考角色接口更新：只更新非 null 的 rname、description。 */
    void updateNamedRole(Long actorId, Long id, String rname, String description);

    /** 旧角色接口删除：逻辑删除并清理授权与账号绑定。 */
    void deleteRole(Long actorId, Long id);

    /** 参考角色接口删除：语义与 {@link #deleteRole} 一致。 */
    void deleteNamedRole(Long actorId, Long id);

    /** 角色绑定的权限编码，按编码排序。 */
    List<String> rolePermissions(Long roleId);

    /** 角色绑定的权限目录记录。 */
    List<AdminPermissionRecord> rolePermissionRecords(Long roleId);

    /** 按权限编码整组替换角色授权；空数组清空。 */
    void replaceRolePermissions(Long actorId, Long roleId, List<String> permissions);

    /** 按权限 ID 整组替换角色授权；空数组清空，重复 ID 去重。 */
    void replaceRolePermissionIds(Long actorId, Long roleId, List<Long> permissionIds);
}
