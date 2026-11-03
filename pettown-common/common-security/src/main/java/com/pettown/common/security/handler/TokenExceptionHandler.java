package com.pettown.common.security.handler;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.pettown.common.result.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TokenExceptionHandler {
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<Void>> handleNotLogin(NotLoginException exception) {
        return ResponseEntity.status(401).body(Result.error("401", "请先登录"));
    }

    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    public ResponseEntity<Result<Void>> handleForbidden(Exception exception) {
        return ResponseEntity.status(403).body(Result.error("403", "无操作权限"));
    }
}
