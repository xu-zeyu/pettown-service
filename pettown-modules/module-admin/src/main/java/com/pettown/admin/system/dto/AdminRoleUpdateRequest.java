package com.pettown.admin.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record AdminRoleUpdateRequest(@NotNull @Positive Long id, @Size(min = 1, max = 50) String rname, @Size(max = 500) String description) {
}
