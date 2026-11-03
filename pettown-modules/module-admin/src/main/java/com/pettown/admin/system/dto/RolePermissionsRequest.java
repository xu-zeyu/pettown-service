package com.pettown.admin.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record RolePermissionsRequest(
        @Schema(description = "权限编码列表；整体替换，空数组清空；仅支持权限目录中的编码，不支持通配符")
        @NotNull @Size(max = 1000) java.util.List<@NotBlank @Size(max = 80) String> permissions) {
}
