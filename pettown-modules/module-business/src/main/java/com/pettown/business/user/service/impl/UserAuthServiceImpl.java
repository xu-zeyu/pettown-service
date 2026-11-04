package com.pettown.business.user.service.impl;

import com.pettown.business.user.entity.User;
import com.pettown.business.user.mapper.UserMapper;
import com.pettown.business.user.service.UserAuthService;
import com.pettown.business.verification.service.VerificationCodeService;
import com.pettown.common.commonEnum.UserSourceEnum;
import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;
import com.pettown.common.commonEnum.VerificationCodeTypeEnum;
import com.pettown.common.utils.PasswordService;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "pettown.user", name = "enabled", havingValue = "true")
public class UserAuthServiceImpl implements UserAuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserMapper userMapper;
    private final VerificationCodeService verificationCodeService;
    private final PasswordService passwordService;

    public UserAuthServiceImpl(UserMapper userMapper, VerificationCodeService verificationCodeService,
            PasswordService passwordService) {
        this.userMapper = userMapper;
        this.verificationCodeService = verificationCodeService;
        this.passwordService = passwordService;
    }

    @Override
    public User getById(String id) {
        return userMapper.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.NOT_FOUND, "用户不存在"));
    }

    @Override
    public User loginOrRegister(String phone, String password, String verificationCode, UserSourceEnum source) {
        verificationCodeService.verifyAndConsume(phone, VerificationCodeTypeEnum.LOGIN_REGISTER, verificationCode);
        User existing = userMapper.findByPhone(phone).orElse(null);
        if (existing != null) {
            return verifyPassword(existing, password);
        }
        String passwordHash = passwordService.passwordEncrypt(password);
        for (int attempt = 0; attempt < 3; attempt++) {
            User user = new User(newUserId(), phone, passwordHash,
                    source == null ? UserSourceEnum.UNKNOWN : source);
            try {
                userMapper.insert(user);
                return user;
            } catch (DuplicateKeyException exception) {
                User concurrentlyCreated = userMapper.findByPhone(phone).orElse(null);
                if (concurrentlyCreated != null) {
                    return verifyPassword(concurrentlyCreated, password);
                }
                if (attempt == 2) {
                    throw exception;
                }
            }
        }
        throw new IllegalStateException("创建用户失败");
    }

    private User verifyPassword(User user, String password) {
        if (!passwordService.passwordValid(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCodeEnum.FAIL, "手机号或密码错误");
        }
        return user;
    }

    private String newUserId() {
        byte[] randomBytes = new byte[12];
        RANDOM.nextBytes(randomBytes);
        try {
            return "pettown" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(randomBytes)).substring(0, 24);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
