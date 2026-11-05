package com.pettown.miniapp.auth.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.pettown.business.user.entity.User;
import com.pettown.business.user.service.UserAuthService;
import com.pettown.common.exception.ErrorCodeEnum;
import com.pettown.common.result.Result;
import com.pettown.common.security.config.PetTownToken;
import com.pettown.common.web.util.UserSourceResolver;
import com.pettown.miniapp.auth.dto.AuthRequest;
import com.pettown.miniapp.auth.vo.AuthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/miniapp/auth")
@Tag(name = "小程序登录管理")
public class AuthController {
    private final UserAuthService userAuthService;

    public AuthController(UserAuthService userAuthService) {
        this.userAuthService = userAuthService;
    }

    @PostMapping("/login")
    @SaIgnore
    @Operation(summary = "手机号密码及验证码登录，首次登录自动注册",
            description = "首次注册的用户来源从 User-Agent 自动识别，无需在请求体传递；后续登录不修改注册来源")
    public Result<AuthResponse> login(@Valid @RequestBody AuthRequest request, HttpServletRequest httpRequest) {
        User user = userAuthService.loginOrRegister(request.phone(), request.password(), request.verificationCode(),
                UserSourceResolver.resolve(httpRequest));
        return Result.success(issueToken(user));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> invalidLoginRequest(MethodArgumentNotValidException error) {
        String detail = error.getBindingResult().getFieldErrors().stream()
                .map(field -> field.getDefaultMessage())
                .filter(message -> message != null && !message.isBlank())
                .findFirst().orElse("登录参数错误");
        return ResponseEntity.badRequest().body(Result.error(ErrorCodeEnum.LOGIN_PARAMETER_INVALID.getCode(), detail));
    }

    private AuthResponse issueToken(User user) {
        PetTownToken.USER.login(user.getId());
        return new AuthResponse(user.getId(), user.getPhone(), PetTownToken.USER.getTokenValue());
    }
}
