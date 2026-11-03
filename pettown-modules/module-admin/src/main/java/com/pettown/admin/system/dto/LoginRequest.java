package com.pettown.admin.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record LoginRequest(
        @Schema(description = "管理员账号")
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{2,49}") String username,
        @Schema(description = "管理员密码", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Size(max = 72) String password) {
}
