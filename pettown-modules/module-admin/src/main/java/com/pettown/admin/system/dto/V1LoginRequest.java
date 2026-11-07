package com.pettown.admin.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record V1LoginRequest(
        @Schema(description = "管理员账号")
        @NotBlank @Pattern(regexp = "(?:[a-z][a-z0-9_]{2,49}|[A-Za-z0-9]{4,50})") String username,
        @Schema(description = "管理员密码", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Size(max = 72) String password,
        @Schema(description = "图形验证码 ID", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank String captchaId,
        @Schema(description = "图形验证码内容", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Size(max = 8) String captchaCode) {
}
