package com.pettown.admin.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import com.pettown.business.admin.entity.AdminRole;
import java.time.LocalDateTime;

public record RoleResponse(
        @Schema(description = "角色 ID") Long id,
        @Schema(description = "唯一角色编码") String code,
        @Schema(description = "角色名称") String name,
        @Schema(description = "是否启用") boolean enabled,
        @Schema(description = "创建时间") LocalDateTime createdTime,
        @Schema(description = "更新时间") LocalDateTime updatedTime) {
    public static RoleResponse from(AdminRole role) {
        return new RoleResponse(role.getId(), role.getCode(), role.getName(), role.getEnabled(),
                role.getCreatedTime(), role.getUpdatedTime());
    }
}
