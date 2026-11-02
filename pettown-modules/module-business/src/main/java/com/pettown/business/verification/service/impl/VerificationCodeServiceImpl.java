package com.pettown.business.verification.service.impl;

import com.pettown.business.verification.service.VerificationCodeService;
import com.pettown.common.cache.Cache;
import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;
import com.pettown.common.verificationEnum.VerificationCodeTypeEnum;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;

@Service
public class VerificationCodeServiceImpl implements VerificationCodeService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CODE_BOUND = 1_000_000;
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final String KEY_PREFIX = "pettown:verification-code:";

    private final Cache cache;

    public VerificationCodeServiceImpl(Cache cache) {
        this.cache = cache;
    }

    @Override
    public String generate(String phone, VerificationCodeTypeEnum type) {
        Assert.hasText(phone, "手机号不能为空");
        Assert.notNull(type, "验证码用途不能为空");
        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(CODE_BOUND));
        cache.set(cacheKey(phone, type), code, CODE_TTL);
        return code;
    }

    @Override
    public void verifyAndConsume(String phone, VerificationCodeTypeEnum type, String code) {
        Assert.hasText(phone, "手机号不能为空");
        Assert.notNull(type, "验证码用途不能为空");
        if (code == null || !code.matches("[0-9]{6}")) {
            throw new BusinessException(ErrorCodeEnum.FAIL, "验证码须为 6 位数字");
        }
        if (!cache.compareAndDelete(cacheKey(phone, type), code)) {
            throw new BusinessException(ErrorCodeEnum.FAIL, "验证码错误或已过期");
        }
    }

    private String cacheKey(String phone, VerificationCodeTypeEnum type) {
        return KEY_PREFIX + type.getValue() + ":" + phone;
    }
}
