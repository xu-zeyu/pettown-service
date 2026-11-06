package com.pettown.admin.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** v1 管理端创建账号；强密码规则由业务层再次校验。 */
public record V1CreateAccountRequest(
        @Schema(description = "唯一用户名：4 到 50 位 ASCII 字母或数字")
        @NotBlank @Pattern(regexp = "[A-Za-z0-9]{4,50}") String username,
        @Schema(description = "显示名：非空且最多 50 个字符")
        @NotBlank @Size(max = 50) String displayName,
        @Schema(description = "初始密码：8 到 72 位非空格 ASCII，须同时包含字母、数字、符号", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Size(min = 8, max = 72) String password,
        @Schema(description = "登录验证码手机号；不传时无法短信登录")
        @Pattern(regexp = "1[3-9][0-9]{9}") String phone,
        @Schema(description = "初始角色 ID；不传时不分配角色")
        @Positive Long roleId) {
}
