package com.pettown.admin.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record V1CreateRoleRequest(
        @Schema(description = "唯一角色编码，3 到 50 位小写字母、数字或下划线，以字母开头；创建后不可修改")
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{2,49}") String code,
        @Schema(description = "角色名称，最多 50 个字符")
        @NotBlank @Size(max = 50) String name,
        @Schema(description = "角色说明，最多 500 个字符")
        @Size(max = 500) String description) {
}
