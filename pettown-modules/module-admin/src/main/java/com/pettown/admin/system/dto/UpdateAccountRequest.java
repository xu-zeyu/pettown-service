package com.pettown.admin.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record UpdateAccountRequest(
        @Schema(description = "显示名；不能为空，最多 50 个字符")
        @NotBlank @Size(max = 50) String displayName,
        @Schema(description = "登录验证码手机号；不传时保持原值，修改后该账号全部旧会话失效")
        @Pattern(regexp = "1[3-9][0-9]{9}") String phone) {
}
