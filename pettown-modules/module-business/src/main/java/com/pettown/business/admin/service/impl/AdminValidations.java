package com.pettown.business.admin.service.impl;

import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;

/** 管理后台输入参数校验；不合法输入统一抛 {@link BusinessException}。 */
final class AdminValidations {

    private AdminValidations() {
    }

    static void validateUsername(String username) {
        if (username == null || !username.matches("[a-z][a-z0-9_]{2,49}")) {
            throw failure("账号须为 3 到 50 位小写字母、数字或下划线，以字母开头");
        }
    }

    static void validatePassword(String password) {
        if (password == null || !password.matches("[\\x21-\\x7E]{8,72}")) {
            throw failure("密码须为 8 到 72 位非空格 ASCII 字符");
        }
    }

    static void validateV1Password(String password) {
        if (password == null || !password.matches("(?=.*[A-Za-z])(?=.*[0-9])[\\x21-\\x7E]{8,64}")) {
            throw failure("密码须为 8 到 64 位非空格 ASCII，且包含字母和数字");
        }
    }

    static void validateName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 50) {
            throw failure("名称不能为空且不能超过 50 个字符");
        }
    }

    static void validatePhone(String phone) {
        if (phone != null && !phone.matches("1[3-9][0-9]{9}")) {
            throw failure("手机号格式不正确");
        }
    }

    static void validateDescription(String description) {
        if (description != null && description.length() > 500) {
            throw failure("角色说明不能超过 500 个字符");
        }
    }

    static void validatePermission(String name, String code) {
        validateName(name);
        if (code == null || !code.matches("[a-zA-Z][a-zA-Z0-9_:/.-]{0,79}")) {
            throw failure("权限编码须为 1 到 80 位字母、数字或 _:/.-，以字母开头，不支持通配符");
        }
    }

    static BusinessException failure(String message) {
        return new BusinessException(ErrorCodeEnum.FAIL, message);
    }
}
