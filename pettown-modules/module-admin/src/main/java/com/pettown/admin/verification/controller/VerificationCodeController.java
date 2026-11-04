package com.pettown.admin.verification.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.pettown.admin.verification.dto.VerificationCodeRequest;
import com.pettown.business.verification.service.VerificationCodeService;
import com.pettown.common.commonEnum.VerificationCodeTypeEnum;
import com.pettown.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mng/auth/verification")
@Tag(name = "管理后台认证")
public class VerificationCodeController {
    @Resource
    private VerificationCodeService verificationCodeService;

    @PostMapping
    @SaIgnore
    @Operation(summary = "按手机号生成并缓存六位验证码",
            description = "公开接口；有效期 5 分钟，按用途和手机号隔离存储，只接受 ADMIN_LOGIN 管理员登录用途；暂不发送短信，不返回验证码")
    public Result<Void> generate(@Valid @RequestBody VerificationCodeRequest request) {
        // 管理后台验证码入口只接受管理员登录用途，与小程序登录/重置密码隔离。
        verificationCodeService.generate(request.phone(), VerificationCodeTypeEnum.ADMIN_LOGIN);
        return Result.success();
    }
}
