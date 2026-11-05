package com.pettown.common.utils;

import cn.dev33.satoken.secure.SaSecureUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/** 生成 SHA-256 密码哈希，并兼容校验已有 BCrypt 哈希。 */
@Service
public class PasswordService {

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder();

    // 加密
    public String passwordEncrypt(String inputPassword) {
        return SaSecureUtil.sha256(inputPassword);
    }

    /**
     * 校验明文密码：优先比对 SHA-256 哈希，失败后按 BCrypt 兼容校验存量哈希。
     */
    public Boolean passwordValid(String inputPassword, String userPassword) {
        if (inputPassword == null || userPassword == null) {
            return false;
        }
        if (userPassword.equals(passwordEncrypt(inputPassword))) {
            return true;
        }
        return BCRYPT.matches(inputPassword, userPassword);
    }
}
