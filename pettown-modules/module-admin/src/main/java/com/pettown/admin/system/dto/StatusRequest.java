package com.pettown.admin.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record StatusRequest(
        @Schema(description = "是否启用；修改状态会使该账号全部旧会话失效")
        @NotNull Boolean enabled) {
}
