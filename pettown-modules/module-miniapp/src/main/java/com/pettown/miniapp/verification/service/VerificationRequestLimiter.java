package com.pettown.miniapp.verification.service;

/** 小程序匿名验证码生成限额。 */
public interface VerificationRequestLimiter {
    void checkAndRecord(String phone, String sourceIp);
}
