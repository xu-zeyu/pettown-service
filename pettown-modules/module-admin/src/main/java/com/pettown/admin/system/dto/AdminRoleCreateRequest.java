package com.pettown.admin.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record AdminRoleCreateRequest(@NotBlank @Size(max = 50) String rname, @Size(max = 500) String description) {
}
