package com.pettown.admin.user.dto;

import com.pettown.business.user.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UserStatusRequest(
        @Schema(description = "ACTIVE 正常；BANNED 永久封禁直到管理员手动解封")
        @NotNull UserStatus status) {
}
