package com.pettown.admin.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record AdminPermissionUpdateRequest(@NotNull @Positive Long id, @Size(min = 1, max = 50) String name, @Size(min = 1, max = 80) String code) {
}
