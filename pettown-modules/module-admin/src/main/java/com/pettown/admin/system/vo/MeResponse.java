package com.pettown.admin.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record MeResponse(
        @Schema(description = "当前管理员信息") AccountResponse account,
        @Schema(description = "已启用的绑定角色；超级管理员权限不依赖角色") List<RoleResponse> roles,
        @Schema(description = "当前有效权限编码的并集") List<String> permissions) {
}
