package com.pettown.miniapp.verification.dto;

import com.pettown.common.commonEnum.VerificationCodeTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record VerificationCodeRequest(
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式错误") String phone,
        @NotNull(message = "验证码用途不能为空")
        @Schema(description = "验证码用途：LOGIN_REGISTER 登录即注册、RESET_PASSWORD 重置密码")
        VerificationCodeTypeEnum type) {
}
