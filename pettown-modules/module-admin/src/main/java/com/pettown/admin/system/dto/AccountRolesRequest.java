package com.pettown.admin.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record AccountRolesRequest(
        @Schema(description = "角色 ID 列表；整体替换绑定，空数组清空，重复 ID 自动去重，最多 100 个")
        @NotNull @Size(max = 100) java.util.List<@NotNull @Positive Long> roleIds) {
}
