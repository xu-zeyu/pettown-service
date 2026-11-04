package com.pettown.admin.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
public record PermissionResponse(
        @Schema(description = "权限编码") String code,
        @Schema(description = "权限名称") String title) {
}
