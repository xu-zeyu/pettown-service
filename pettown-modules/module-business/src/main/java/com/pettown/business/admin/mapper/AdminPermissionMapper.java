package com.pettown.business.admin.mapper;

import com.pettown.business.admin.entity.AdminPermissionRecord;
import com.pettown.common.mybatis.mapper.BaseMapperX;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 权限目录数据访问。 */
public interface AdminPermissionMapper extends BaseMapperX<AdminPermissionRecord> {

    /**
     * 按角色 ID 查询其绑定的权限目录记录（admin_role_permission × admin_permission 联表）。
     */
    List<AdminPermissionRecord> selectByRoleIds(@Param("roleIds") Collection<Long> roleIds);
}
