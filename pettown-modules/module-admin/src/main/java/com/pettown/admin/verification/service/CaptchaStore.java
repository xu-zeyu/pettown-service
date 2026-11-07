package com.pettown.admin.verification.service;

/** 图形验证码暂存；pop 一次性消费（读取即删除），过期由存储 TTL 保证。 */
public interface CaptchaStore {
    void save(String captchaId, String code);

    /** 读取并删除该验证码；不存在或已过期返回 null。 */
    String pop(String captchaId);
}
