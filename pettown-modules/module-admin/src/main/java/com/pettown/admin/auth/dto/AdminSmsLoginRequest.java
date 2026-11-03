package com.pettown.admin.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record AdminSmsLoginRequest(@NotBlank @Size(max = 50) String username,
        @Schema(description = "管理员密码", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Size(max = 72) String password,
        @Schema(description = "管理员登录用途的六位短信验证码", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Pattern(regexp = "[0-9]{6}") String smsCode) {
}
