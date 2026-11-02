package com.pettown.business.verification.service;

import com.pettown.common.verificationEnum.VerificationCodeTypeEnum;

public interface VerificationCodeService {
    /** 生成并缓存验证码，返回值供服务端后续发送使用，不应在客户端接口返回。 */
    String generate(String phone, VerificationCodeTypeEnum type);

    /** 校验并消费指定用途的验证码；错误、过期或已使用时抛出业务异常。 */
    void verifyAndConsume(String phone, VerificationCodeTypeEnum type, String code);
}
