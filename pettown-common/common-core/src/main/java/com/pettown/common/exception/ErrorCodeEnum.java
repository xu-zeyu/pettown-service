package com.pettown.common.exception;

public enum ErrorCodeEnum implements CommonErrorCode {
    SUCCESS("200", "操作成功"),
    FAIL("400", "操作失败"),
    TOO_MANY_REQUESTS("429", "请求过于频繁"),
    LOGIN_PARAMETER_INVALID("4001", "登录参数错误"),
    LOGIN_VERIFICATION_INVALID("4002", "登录验证码错误"),
    LOGIN_PASSWORD_INVALID("4003", "手机号或密码错误"),
    LOGIN_LOCKED("423", "账号暂时锁定"),
    PET_LIMIT_REACHED("409", "宠物数量已达上限"),
    ADMIN_USERNAME_EXISTS("4091", "管理员账号已存在"),
    NOT_FOUND("404", "资源不存在"),
    SERVICE_UNAVAILABLE("503", "服务暂不可用");

    private final String code;
    private final String message;

    ErrorCodeEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
}
