package com.pettown.miniapp.auth.controller;

import com.pettown.business.user.entity.User;
import com.pettown.business.user.service.UserAuthService;
import com.pettown.common.result.Result;
import com.pettown.common.security.config.PetTownToken;
import com.pettown.miniapp.auth.dto.AuthRequest;
import com.pettown.miniapp.auth.vo.AuthResponse;
import com.pettown.miniapp.user.vo.UserInfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
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
    @Operation(summary = "手机号密码及验证码登录，首次登录自动注册")
    public Result<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        User user = userAuthService.loginOrRegister(request.phone(), request.password(), request.verificationCode());
        return Result.success(issueToken(user));
    }

    private AuthResponse issueToken(User user) {
        PetTownToken.USER.login(user.getId());
        return new AuthResponse(user.getId(), user.getPhone(), PetTownToken.USER.getTokenValue());
    }
}
