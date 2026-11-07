package com.pettown.admin.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record V1UpdateAccountRequest(
        @Schema(description = "显示名；不传或 null 表示不修改")
        @Size(min = 1, max = 50) String displayName,
        @Schema(description = "手机号；不传或 null 表示不修改，变更会使目标账号旧会话失效")
        @Pattern(regexp = "1[3-9][0-9]{9}") String phone,
        @Schema(description = "关联角色 ID；不传或 null 表示不修改，分配需 system:account:assign-role")
        Long roleId) {
}
