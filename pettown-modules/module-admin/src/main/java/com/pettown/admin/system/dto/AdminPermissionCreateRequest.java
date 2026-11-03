package com.pettown.admin.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record AdminPermissionCreateRequest(@NotBlank @Size(max = 50) String name, @NotBlank @Size(max = 80) String code) {
}
