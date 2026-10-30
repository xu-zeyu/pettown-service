package com.pettown.common.exception;

public enum ErrorCodeEnum implements CommonErrorCode {
    SUCCESS("200", "操作成功"),
    FAIL("400", "操作失败"),
    NOT_FOUND("404", "资源不存在");

    private final String code;
    private final String message;

    ErrorCodeEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
}
