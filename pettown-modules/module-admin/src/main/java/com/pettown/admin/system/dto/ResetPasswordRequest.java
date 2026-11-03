package com.pettown.admin.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record ResetPasswordRequest(
        @Schema(description = "重置后的密码；8 到 72 位非空格 ASCII 字符，下次登录须修改", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Pattern(regexp = "[\\x21-\\x7E]{8,72}") String password) {
}
