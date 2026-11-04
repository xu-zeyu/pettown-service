package com.pettown.admin.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record AdminPermissionSyncRequest(@Schema(description = "权限目录；仅新增不存在的编码，已有编码保持原名称，不删除权限")
        @NotEmpty @Size(max = 1000) java.util.List<@NotNull @Valid AdminPermissionCreateRequest> permissions) {
}
