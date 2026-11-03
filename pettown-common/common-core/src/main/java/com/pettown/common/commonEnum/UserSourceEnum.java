package com.pettown.common.commonEnum;


import lombok.Getter;

/*
* 用户来源枚举类
* */
@Getter
public enum UserSourceEnum {
    IOS("ios"),
    ANDROID("android"),
    HARMONY_OS("harmonyOs"),
    MINI_PROGRAM("miniProgram"),
    WEB("web"),
    UNKNOWN("unknown");

    private final String value;

    UserSourceEnum(String value) {
        this.value = value;
    }
}
