package com.pettown.admin.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import com.pettown.business.admin.entity.AdminAccount;
import java.time.LocalDateTime;

public record AccountResponse(
        @Schema(description = "管理员 ID") Long id,
        @Schema(description = "管理员账号") String username,
        @Schema(description = "显示名") String displayName,
        @Schema(description = "关联角色 ID；一个账号最多关联一个角色，未关联时为 null") Long roleId,
        @Schema(description = "登录验证码手机号；未配置时无法短信登录") String phone,
        @Schema(description = "是否启用") boolean enabled,
        @Schema(description = "是否为初始超级管理员") boolean superAdmin,
        @Schema(description = "是否必须先修改密码") boolean passwordChangeRequired,
        @Schema(description = "创建时间") LocalDateTime createdTime,
        @Schema(description = "更新时间") LocalDateTime updatedTime) {
    public static AccountResponse from(AdminAccount account) {
        return new AccountResponse(account.getId(), account.getUsername(), account.getDisplayName(), account.getRoleId(), account.getPhone(),
                account.getEnabled(), account.getSuperAdmin(), account.getPasswordChangeRequired(),
                account.getCreatedTime(), account.getUpdatedTime());
    }
}
