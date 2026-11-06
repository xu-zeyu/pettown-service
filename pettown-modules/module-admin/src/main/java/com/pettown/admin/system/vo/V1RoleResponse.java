package com.pettown.admin.system.vo;

import com.pettown.business.admin.entity.AdminRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record V1RoleResponse(
        @Schema(description = "角色 ID") Long id,
        @Schema(description = "唯一角色编码，创建后不可修改") String code,
        @Schema(description = "角色名称") String name,
        @Schema(description = "角色说明") String description,
        @Schema(description = "是否启用") boolean enabled,
        @Schema(description = "创建时间") LocalDateTime createdTime,
        @Schema(description = "更新时间") LocalDateTime updatedTime) {
    public static V1RoleResponse from(AdminRole role) {
        return new V1RoleResponse(role.getId(), role.getCode(), role.getName(), role.getDescription(),
                role.getEnabled(), role.getCreatedTime(), role.getUpdatedTime());
    }
}
