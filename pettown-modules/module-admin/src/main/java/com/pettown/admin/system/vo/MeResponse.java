package com.pettown.admin.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record MeResponse(
        @Schema(description = "当前管理员信息") AccountResponse account,
        @Schema(description = "已启用的关联角色，最多一项；超级管理员权限不依赖角色") List<RoleResponse> roles,
        @Schema(description = "当前关联角色提供的有效权限编码") List<String> permissions) {
}
