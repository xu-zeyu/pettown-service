package com.pettown.admin.system.vo;

import com.pettown.business.admin.entity.AdminRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record AdminRoleResponse(Long id, @Schema(description = "角色名称") String rname,
        String description, LocalDateTime updatedTime, LocalDateTime createdTime,
        @Schema(description = "0 为正常，1 为已删除；列表只返回正常角色") int deleted) {
    public static AdminRoleResponse from(AdminRole role) {
        return new AdminRoleResponse(role.getId(), role.getName(), role.getDescription(),
                role.getUpdatedTime(), role.getCreatedTime(), role.getDeleted());
    }
}
