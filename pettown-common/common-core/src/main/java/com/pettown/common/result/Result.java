package com.pettown.common.result;

import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;

public record Result<T>(String code, String msg, T data) {
    public static <T> Result<T> success(T data) {
        return new Result<>(ErrorCodeEnum.SUCCESS.getCode(), ErrorCodeEnum.SUCCESS.getMessage(), data);
    }

    public static Result<Void> error(BusinessException exception) {
        return new Result<>(exception.getCommonErrorCode().getCode(), exception.getMessage(), null);
    }

    public static Result<Void> error(String code, String message) {
        return new Result<>(code, message, null);
    }
}
