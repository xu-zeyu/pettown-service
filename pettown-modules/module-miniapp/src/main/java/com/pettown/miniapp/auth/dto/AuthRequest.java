package com.pettown.miniapp.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AuthRequest(
        @Schema(description = "中国大陆手机号")
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式错误") String phone,
        @Schema(description = "账号密码；8 到 72 位非空格 ASCII 字符", format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank(message = "密码不能为空")
        @Pattern(regexp = "^[\\x21-\\x7E]{8,72}$", message = "密码须为 8 到 72 位非空格字符") String password,
        @Schema(description = "六位数字验证码；登录即注册用途", accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank(message = "验证码不能为空")
        @Pattern(regexp = "^[0-9]{6}$", message = "验证码须为 6 位数字") String verificationCode) {
}
