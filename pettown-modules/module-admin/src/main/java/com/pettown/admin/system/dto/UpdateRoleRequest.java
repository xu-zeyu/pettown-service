package com.pettown.admin.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record UpdateRoleRequest(
        @Schema(description = "角色名称；不能为空，最多 50 个字符")
        @NotBlank @Size(max = 50) String name,
        @Schema(description = "是否启用；禁用后不再提供角色权限")
        @NotNull Boolean enabled) {
}
