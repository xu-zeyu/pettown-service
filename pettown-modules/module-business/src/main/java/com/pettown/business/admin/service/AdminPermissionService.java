package com.pettown.business.admin.service;

import com.pettown.business.admin.entity.AdminPermissionRecord;
import com.pettown.common.mybatis.PageParam;
import com.pettown.common.mybatis.PageResult;
import java.util.List;

/** 权限目录：内置编码不可改名/删除，自定义编码支持维护与批量同步。 */
public interface AdminPermissionService {

    PageResult<AdminPermissionRecord> listPermissions(PageParam page);

    AdminPermissionRecord getPermission(Long id);

    /** 创建自定义权限；编码唯一且不支持通配符。 */
    void createPermission(Long actorId, String name, String code);

    /**
     * 局部更新权限；内置编码不可改名。
     * 非超管只能修改自己持有的权限，改码时还须持有目标编码；改码同事务迁移角色绑定。
     */
    void updatePermission(Long actorId, Long id, String name, String code);

    /** 删除自定义权限并清理角色绑定；内置权限不可删除。 */
    void deletePermission(Long actorId, Long id);

    /** 批量同步：只新增缺失编码，已有名称不覆盖、不删除旧项，同批重复编码只创建一次。 */
    void syncPermissions(Long actorId, List<AdminService.PermissionDefinition> permissions);
}
