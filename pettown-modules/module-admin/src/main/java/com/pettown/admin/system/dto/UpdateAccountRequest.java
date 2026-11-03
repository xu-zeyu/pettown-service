package com.pettown.admin.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record UpdateAccountRequest(
        @Schema(description = "显示名；不能为空，最多 50 个字符")
        @NotBlank @Size(max = 50) String displayName) {
}
