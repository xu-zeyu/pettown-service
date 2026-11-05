package com.pettown.business.user.service;

/** 登录密码失败锁定策略；验证码失败不应计入此策略。 */
public interface LoginFailureGuard {
    void assertNotLocked(String phone);

    void recordPasswordFailure(String phone);

    void clear(String phone);
}
