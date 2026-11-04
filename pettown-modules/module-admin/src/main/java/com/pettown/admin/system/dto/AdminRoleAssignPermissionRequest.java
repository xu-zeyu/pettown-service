package com.pettown.admin.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record AdminRoleAssignPermissionRequest(@NotNull @Positive Long roleId,
        @Schema(description = "权限 ID 列表；整体替换，空数组清空，重复 ID 去重")
        @NotNull @Size(max = 1000) java.util.List<@NotNull @Positive Long> permissionIds) {
}
