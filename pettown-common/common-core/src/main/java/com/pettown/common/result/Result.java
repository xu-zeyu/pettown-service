package com.pettown.common.result;

import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.CommonErrorCode;
import com.pettown.common.exception.ErrorCodeEnum;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 类名: Result
 * 描述: 公共 api 响应体
 * 作者: xuzeyu
 * 创建时间: 2025/12/22
 */
@Data
@NoArgsConstructor
public class Result<T> {
    /*
     * 响应码
     * */
    private String code;

    /*
     * 响应信息
     * */
    private String msg;


    /*
     * 响应数据
     * */
    private T data;

    private Result(String code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    private Result(CommonErrorCode errorCode, T data) {
        this(errorCode);
        this.data = data;
    }

    public Result(CommonErrorCode errorCode) {
        this.code = errorCode.getCode();
        this.msg = errorCode.getMessage();
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ErrorCodeEnum.SUCCESS,data);
    }

    public static <T> Result<T> success() {
        return new Result<>(ErrorCodeEnum.SUCCESS);
    }

    public static <T> Result<T> error(BusinessException exception) {
        return new Result<>(exception.getCommonErrorCode().getCode(),exception.getMessage());
    }

    public static Result<Void> error(String code, String msg) {
        return new Result<>(code, msg);
    }

    public static <T> Result<T> build(String code, String msg) {
        return new Result<>(code,msg);
    }

}
