package com.pettown.admin.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record V1UpdateRoleRequest(
        @Schema(description = "角色名称，最多 50 个字符")
        @NotBlank @Size(max = 50) String name,
        @Schema(description = "角色说明；null 清空")
        @Size(max = 500) String description,
        @Schema(description = "是否启用；禁用后角色权限对后续请求立即失效")
        @NotNull Boolean enabled) {
}
