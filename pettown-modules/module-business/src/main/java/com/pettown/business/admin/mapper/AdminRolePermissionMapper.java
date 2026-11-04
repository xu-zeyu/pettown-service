package com.pettown.business.admin.mapper;

import com.pettown.business.admin.entity.AdminRolePermission;
import com.pettown.common.mybatis.mapper.BaseMapperX;
import java.util.Collection;
import org.apache.ibatis.annotations.Param;

/** 角色与权限编码绑定关系数据访问。 */
public interface AdminRolePermissionMapper extends BaseMapperX<AdminRolePermission> {

    /**
     * 删除角色的全部权限绑定。
     */
    int deleteRolePermissions(@Param("roleId") Long roleId);

    /**
     * 批量插入角色权限绑定；permissionCodes 已由调用方去重。
     */
    int batchInsertRolePermissions(@Param("roleId") Long roleId,
            @Param("permissionCodes") Collection<String> permissionCodes);
}
