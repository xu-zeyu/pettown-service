package com.pettown.admin.system.vo;

import com.pettown.business.admin.entity.AdminAccount;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record AdminInfoResponse(Long id, String username, String realName, String phone, String avatar,
        LocalDateTime createdTime, LocalDateTime lastLoginTime, LocalDateTime updatedTime,
        @Schema(description = "账号关联的已启用角色；未关联、已禁用或已删除时为 null")
        AdminRoleResponse role) {
    public static AdminInfoResponse from(AdminAccount account, AdminRoleResponse role) {
        return new AdminInfoResponse(account.getId(), account.getUsername(), account.getDisplayName(),
                account.getPhone(), account.getAvatar(), account.getCreatedTime(), account.getLastLoginTime(),
                account.getUpdatedTime(), role);
    }
}
