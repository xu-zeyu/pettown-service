package com.pettown.admin.auth.service;

/** 管理员登录密码失败锁定；连续 5 次失败锁定 30 分钟，成功登录清零。 */
public interface AdminLoginFailureGuard {
    void assertNotLocked(String username);

    /** 记录一次密码失败；达到 5 次时抛出锁定业务异常。 */
    void recordPasswordFailure(String username);

    void clear(String username);
}
