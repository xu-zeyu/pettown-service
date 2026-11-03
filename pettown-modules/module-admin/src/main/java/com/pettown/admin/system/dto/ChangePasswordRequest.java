package com.pettown.admin.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record ChangePasswordRequest(
        @Schema(description = "原密码", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Size(max = 72) String oldPassword,
        @Schema(description = "新密码；8 到 72 位非空格 ASCII 字符，不能与原密码相同", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Pattern(regexp = "[\\x21-\\x7E]{8,72}") String newPassword) {
}
