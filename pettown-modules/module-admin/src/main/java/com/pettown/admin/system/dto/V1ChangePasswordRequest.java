package com.pettown.admin.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** v1 管理端本人修改密码参数。 */
public record V1ChangePasswordRequest(
        @Schema(description = "原密码", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank String oldPassword,
        @Schema(description = "新密码：8 到 64 位非空格 ASCII，至少包含字母和数字，且不能与原密码相同", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Pattern(regexp = "(?=.*[A-Za-z])(?=.*[0-9])[\\x21-\\x7E]{8,64}") String newPassword) {
}
