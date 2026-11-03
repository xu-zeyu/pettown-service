package com.pettown.miniapp.verification.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.pettown.business.verification.service.VerificationCodeService;
import com.pettown.common.result.Result;
import com.pettown.miniapp.verification.dto.VerificationCodeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/miniapp/verification/code")
@Tag(name = "小程序登录管理")
public class VerificationCodeController {
    @Resource
    private  VerificationCodeService verificationCodeService;

    @PostMapping
    @SaIgnore
    @Operation(summary = "生成并缓存六位验证码", description = "有效期 5 分钟，按用途和手机号隔离存储，暂不发送短信")
    public Result<Void> generate(@Valid @RequestBody VerificationCodeRequest request) {
        verificationCodeService.generate(request.phone(), request.type());
        return Result.success();
    }
}
