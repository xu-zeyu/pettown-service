package com.pettown.admin.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record AdminRoleDeleteRequest(@NotNull @Positive Long id) {
}
