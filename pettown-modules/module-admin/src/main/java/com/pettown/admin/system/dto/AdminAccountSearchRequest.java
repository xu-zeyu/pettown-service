package com.pettown.admin.system.dto;

import com.pettown.common.mybatis.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** v1 管理端账号列表的独立筛选条件。 */
public class AdminAccountSearchRequest extends PageParam {
    @Schema(description = "账号模糊查询，最多 50 个字符")
    @Size(max = 50)
    private String username;

    @Schema(description = "显示名模糊查询，最多 50 个字符")
    @Size(max = 50)
    private String displayName;

    @Schema(description = "账号启用状态；不传时查询全部")
    private Boolean enabled;

    @Schema(description = "当前关联角色 ID 精确匹配；不传时不限角色")
    @Positive
    private Long roleId;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
}
