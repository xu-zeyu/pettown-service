package com.pettown.admin.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AdminSelfResponse(String avatar, String userName,
        @Schema(description = "当前有效权限编码；前端据此控制菜单及按钮，接口仍独立鉴权") List<String> authorities) {
}
