package com.pettown.admin.verification.service;

/** 图形验证码签发限流；超出额度抛业务异常。 */
public interface CaptchaRateLimiter {
    void checkAndRecord(String clientIp);
}
