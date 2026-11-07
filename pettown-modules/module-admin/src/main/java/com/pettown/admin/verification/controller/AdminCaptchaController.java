package com.pettown.admin.verification.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.pettown.admin.verification.service.AdminCaptchaService;
import com.pettown.admin.verification.service.CaptchaClientAddressResolver;
import com.pettown.admin.verification.service.CaptchaRateLimiter;
import com.pettown.admin.verification.vo.CaptchaResponse;
import com.pettown.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/verification")
@Tag(name = "管理端图形验证码")
public class AdminCaptchaController {
    private final AdminCaptchaService captchaService;
    private final CaptchaRateLimiter rateLimiter;
    private final CaptchaClientAddressResolver addressResolver;

    public AdminCaptchaController(AdminCaptchaService captchaService, CaptchaRateLimiter rateLimiter,
            CaptchaClientAddressResolver addressResolver) {
        this.captchaService = captchaService;
        this.rateLimiter = rateLimiter;
        this.addressResolver = addressResolver;
    }

    @SaIgnore
    @GetMapping("/captcha")
    @Operation(summary = "获取登录图形验证码", description = "公开接口；返回验证码 ID 与 Base64 PNG 图片，5 分钟有效且一次性；同一 IP 每分钟最多 20 次")
    public Result<CaptchaResponse> captcha(HttpServletRequest request) {
        rateLimiter.checkAndRecord(addressResolver.resolve(request));
        return Result.success(captchaService.issue());
    }
}
