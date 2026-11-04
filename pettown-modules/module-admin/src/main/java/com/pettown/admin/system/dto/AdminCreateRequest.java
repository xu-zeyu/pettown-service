package com.pettown.admin.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record AdminCreateRequest(@NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{2,49}") String username,
        @Schema(description = "8 到 72 位非空格 ASCII 字符", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Pattern(regexp = "[\\x21-\\x7E]{8,72}") String password,
        @Schema(description = "登录验证码使用的手机号") @Pattern(regexp = "1[3-9][0-9]{9}") String phone,
        @Size(max = 50) String realName,
        @Schema(description = "初始角色 ID；不传时不分配角色") @Positive Long roleId) {
}
