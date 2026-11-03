package com.pettown.admin.system.vo;

import com.pettown.business.admin.entity.AdminPermissionRecord;
import io.swagger.v3.oas.annotations.media.Schema;

public record AdminPermissionResponse(@Schema(description = "权限 ID") Long id,
        @Schema(description = "权限名称") String name, @Schema(description = "权限编码") String code) {
    public static AdminPermissionResponse from(AdminPermissionRecord permission) {
        return new AdminPermissionResponse(permission.getId(), permission.getName(), permission.getCode());
    }
}
