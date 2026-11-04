package com.pettown.business.admin.service;

import com.pettown.business.admin.entity.AdminAccount;

/** 管理员认证：短信验证码生成与“密码 + 验证码”登录校验。 */
public interface AdminAuthService {

    /**
     * 按用户名为启用账号生成管理员登录验证码（六位数字、五分钟、按手机号隔离）。
     * 只写入缓存；短信投递由后续发送服务接入，禁止向客户端返回验证码。
     */
    void generateLoginCode(String username);

    /** 账号密码校验。 */
    AdminAccount authenticate(String username, String password);

    /** 密码与短信验证码双因子校验；先校验密码再原子消费验证码。 */
    AdminAccount authenticateWithCode(String username, String password, String smsCode);
}
