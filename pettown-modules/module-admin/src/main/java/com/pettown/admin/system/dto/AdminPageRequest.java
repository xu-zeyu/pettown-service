package com.pettown.admin.system.dto;

import com.pettown.common.mybatis.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public class AdminPageRequest extends PageParam {
    @Schema(description = "按账号/显示名或角色编码/名称模糊查询，最多 50 个字符")
    @Size(max = 50)
    private String keyword;
    @Schema(description = "是否启用；不传时查询全部状态")
    private Boolean enabled;

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
