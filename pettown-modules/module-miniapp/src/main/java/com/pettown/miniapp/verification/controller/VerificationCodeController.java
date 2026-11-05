package com.pettown.miniapp.verification.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.pettown.business.verification.service.VerificationCodeService;
import com.pettown.common.commonEnum.VerificationCodeTypeEnum;
import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;
import com.pettown.common.result.Result;
import com.pettown.miniapp.verification.dto.VerificationCodeRequest;
import com.pettown.miniapp.verification.service.VerificationRequestLimiter;
import com.pettown.miniapp.verification.service.VerificationClientAddressResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/miniapp/verification")
@Tag(name = "小程序登录管理")
public class VerificationCodeController {
    private final VerificationCodeService verificationCodeService;
    private final VerificationRequestLimiter limiter;
    private final VerificationClientAddressResolver addressResolver;

    public VerificationCodeController(VerificationCodeService verificationCodeService,
            VerificationRequestLimiter limiter, VerificationClientAddressResolver addressResolver) {
        this.verificationCodeService = verificationCodeService;
        this.limiter = limiter;
        this.addressResolver = addressResolver;
    }

    @PostMapping({"/send", "/code"})
    @SaIgnore
    @Operation(summary = "写入小程序验证码至 Redis", description = "验证码有效期 5 分钟，仅供从 Redis 获取；未发送短信。/code 为兼容路径并共用限流")
    public Result<Void> generate(@Valid @RequestBody VerificationCodeRequest request,
            HttpServletRequest servletRequest) {
        if (request.type() == VerificationCodeTypeEnum.ADMIN_LOGIN) {
            throw new BusinessException(ErrorCodeEnum.FAIL, "小程序不支持管理员验证码用途");
        }
        limiter.checkAndRecord(request.phone(), addressResolver.resolve(servletRequest));
        verificationCodeService.generate(request.phone(), request.type());
        return Result.build(ErrorCodeEnum.SUCCESS.getCode(), "验证码已写入 Redis，未发送短信");
    }
}
