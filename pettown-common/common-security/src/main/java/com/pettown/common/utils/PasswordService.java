package com.pettown.common.utils;

import cn.dev33.satoken.secure.SaSecureUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/** 生成 SHA-256 密码哈希，并兼容校验已有 BCrypt 哈希。 */
@Service
public class PasswordService {
    // 加密
    public String passwordEncrypt(String inputPassword) {
        return SaSecureUtil.sha256(inputPassword);
    }

    /**
     * 解密
     */
    public Boolean passwordValid(String inputPassword,String userPassword) {
        String encryptPwd = passwordEncrypt(inputPassword);
        return userPassword.equals(encryptPwd);
    }
}
