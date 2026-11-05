package com.pettown.business.admin.service.impl;

import static com.pettown.business.admin.service.impl.AdminValidations.failure;

import com.pettown.business.admin.entity.AdminAccount;
import com.pettown.business.admin.mapper.AdminAccountMapper;
import com.pettown.business.admin.service.AdminAuthService;
import com.pettown.business.verification.service.VerificationCodeService;
import com.pettown.common.commonEnum.VerificationCodeTypeEnum;
import com.pettown.common.utils.PasswordService;
import org.springframework.stereotype.Service;

/** 管理员认证实现：密码 + 短信验证码双因子，验证码错误不消耗正确值。 */
@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    private final AdminAccountMapper accounts;
    private final VerificationCodeService verification;
    private final PasswordService passwordService;

    public AdminAuthServiceImpl(AdminAccountMapper accounts, VerificationCodeService verification,
            PasswordService passwordService) {
        this.accounts = accounts;
        this.verification = verification;
        this.passwordService = passwordService;
    }

    @Override
    public void generateLoginCode(String username) {
        AdminAccount account = accounts.selectOne(AdminAccount::getUsername, username);
        if (account == null || !Boolean.TRUE.equals(account.getEnabled())
                || account.getPhone() == null || !account.getPhone().matches("1[3-9][0-9]{9}")) {
            throw failure("管理员不存在、已禁用或未配置有效手机号");
        }
        // 只生成缓存；短信投递由后续发送服务接入，禁止在 HTTP 响应或日志中输出验证码。
        verification.generate(account.getPhone(), VerificationCodeTypeEnum.ADMIN_LOGIN);
    }

    @Override
    public AdminAccount authenticate(String username, String password) {
        AdminAccount account = accounts.selectOne(AdminAccount::getUsername, username);
        if (account == null) {
            throw failure("账号或密码错误");
        }
        Boolean passwordValid = passwordService.passwordValid(password, account.getPasswordHash());
        if (!Boolean.TRUE.equals(account.getEnabled()) || !Boolean.TRUE.equals(passwordValid)) {
            throw failure("账号或密码错误");
        }
        return account;
    }

    @Override
    public AdminAccount authenticateWithCode(String username, String password, String smsCode) {
        AdminAccount account = authenticate(username, password);
        if (account.getPhone() == null || account.getPhone().isBlank()) {
            throw failure("管理员未配置手机号");
        }
        // 密码校验通过后原子消费，错误验证码不删除正确值，成功后不可重放。
        verification.verifyAndConsume(account.getPhone(), VerificationCodeTypeEnum.ADMIN_LOGIN, smsCode);
        return account;
    }
}
