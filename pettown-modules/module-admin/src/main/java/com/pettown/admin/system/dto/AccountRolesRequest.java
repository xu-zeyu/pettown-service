package com.pettown.admin.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record AccountRolesRequest(
        @Schema(description = "兼容旧接口的角色 ID 数组；最多一项，空数组解除关联，多个角色会被拒绝")
        @NotNull @Size(max = 1) java.util.List<@NotNull @Positive Long> roleIds) {
}
