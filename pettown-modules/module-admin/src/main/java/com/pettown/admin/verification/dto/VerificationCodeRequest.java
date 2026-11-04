package com.pettown.admin.verification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record VerificationCodeRequest(
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式错误") String phone,
        @NotNull(message = "验证码用途不能为空")
        @Schema(description = "验证码用途；管理后台只接受 ADMIN_LOGIN 管理员登录")
        Type type) {

    /** 管理后台验证码用途；只接受管理员登录。 */
    public enum Type {
        ADMIN_LOGIN
    }
}
