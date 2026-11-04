package com.pettown.admin.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record CreateAccountRequest(
        @Schema(description = "唯一账号；3 到 50 位小写字母、数字或下划线，以字母开头，创建后不可修改")
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{2,49}") String username,
        @Schema(description = "显示名；不能为空，最多 50 个字符")
        @NotBlank @Size(max = 50) String displayName,
        @Schema(description = "初始密码；8 到 72 位非空格 ASCII 字符，首次登录须修改", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank @Pattern(regexp = "[\\x21-\\x7E]{8,72}") String password,
        @Schema(description = "登录验证码手机号；未配置时无法短信登录")
        @Pattern(regexp = "1[3-9][0-9]{9}") String phone) {
}
