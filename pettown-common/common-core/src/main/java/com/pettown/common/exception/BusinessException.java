package com.pettown.common.exception;

public class BusinessException extends RuntimeException {
    private final CommonErrorCode commonErrorCode;

    public BusinessException(CommonErrorCode code, String message) {
        super(message);
        this.commonErrorCode = code;
    }

    public CommonErrorCode getCommonErrorCode() { return commonErrorCode; }
}
