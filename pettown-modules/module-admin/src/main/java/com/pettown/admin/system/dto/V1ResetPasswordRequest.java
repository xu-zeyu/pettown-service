package com.pettown.admin.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** v1 管理端重置密码参数。 */
public record V1ResetPasswordRequest(
        @Schema(description = "新密码：8 到 64 位非空格 ASCII，至少包含字母和数字；下次登录须修改", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Pattern(regexp = "(?=.*[A-Za-z])(?=.*[0-9])[\\x21-\\x7E]{8,64}") String password) {
}
