package com.pettown.admin.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;

public record AccountRoleRequest(
        @Schema(description = "角色 ID；一个账号最多关联一个角色，null 或不传时解除关联")
        @Positive Long roleId) {
}
