package com.pettown.common.verificationEnum;

import lombok.Getter;

@Getter
public enum VerificationCodeTypeEnum {
    LOGIN_REGISTER("login_register"),
    RESET_PASSWORD("reset_password");

    private final String value;

    VerificationCodeTypeEnum(String value) {
        this.value = value;
    }

}
