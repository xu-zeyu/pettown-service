package com.pettown.admin.auth.service;

import com.pettown.admin.auth.controller.AdminAuthController;
import com.pettown.admin.system.vo.AccountResponse;
import com.pettown.admin.system.vo.LoginResponse;
import com.pettown.admin.verification.service.AdminCaptchaService;
import com.pettown.business.admin.entity.AdminAccount;
import com.pettown.business.admin.service.AdminService;
import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;
import com.pettown.common.security.config.PetTownToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 管理员登录：锁定检查 → 图形验证码一次性校验 → 密码校验；验证码错误不进入密码校验。 */
@Service
public class AdminLoginService {
    private final AdminService adminService;
    private final AdminCaptchaService captchaService;
    private final AdminLoginFailureGuard failureGuard;

    @Autowired
    public AdminLoginService(AdminService adminService, AdminCaptchaService captchaService,
            AdminLoginFailureGuard failureGuard) {
        this.adminService = adminService;
        this.captchaService = captchaService;
        this.failureGuard = failureGuard;
    }

    public LoginResponse login(String username, String password, String captchaId, String captchaCode) {
        failureGuard.assertNotLocked(username);
        if (!captchaService.consume(captchaId, captchaCode)) {
            throw new BusinessException(ErrorCodeEnum.LOGIN_VERIFICATION_INVALID, "验证码错误或已失效");
        }
        AdminAccount account;
        try {
            account = adminService.authenticate(username, password);
        } catch (BusinessException exception) {
            failureGuard.recordPasswordFailure(username);
            throw exception;
        }
        failureGuard.clear(username);
        adminService.recordLogin(account.getId());
        PetTownToken.ADMIN.login(account.getId());
        PetTownToken.ADMIN.getTokenSession().set(AdminAuthController.SESSION_VERSION, account.getSessionVersion());
        return new LoginResponse("Bearer", PetTownToken.ADMIN.getTokenValue(), PetTownToken.ADMIN.getTokenTimeout(),
                AccountResponse.from(account));
    }
}
