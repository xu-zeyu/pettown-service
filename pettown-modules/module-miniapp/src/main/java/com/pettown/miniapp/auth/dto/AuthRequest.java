package com.pettown.miniapp.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AuthRequest(
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式错误") String phone,
        @NotBlank(message = "密码不能为空")
        @Pattern(regexp = "^[\\x21-\\x7E]{8,72}$", message = "密码须为 8 到 72 位非空格字符") String password) {
}
