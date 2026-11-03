package com.pettown.admin.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
public record LoginResponse(
        @Schema(description = "Token 类型，固定为 Bearer") String tokenType,
        @Schema(description = "管理员 Token；通过 Authorization 请求头传递，仅登录时返回") String token,
        @Schema(description = "当前 Token 剩余有效期，单位：秒") long expiresIn,
        @Schema(description = "登录账号信息；passwordChangeRequired 为 true 时必须先修改密码") AccountResponse account) {
}
