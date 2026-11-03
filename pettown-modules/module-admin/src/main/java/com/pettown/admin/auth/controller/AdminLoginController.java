package com.pettown.admin.auth.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.pettown.admin.auth.dto.AdminSmsLoginRequest;
import com.pettown.admin.system.vo.AdminInfoResponse;
import com.pettown.admin.system.vo.AdminRoleResponse;
import com.pettown.admin.system.vo.AdminSelfResponse;
import com.pettown.business.admin.service.AdminService;
import com.pettown.common.log.annotation.Log;
import com.pettown.common.result.Result;
import com.pettown.common.security.config.PetTownToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/admin")
@Tag(name = "管理员登录与个人信息")
public class AdminLoginController {
    private final AdminService service;

    public AdminLoginController(AdminService service) {
        this.service = service;
    }

    @PostMapping("/login/sms")
    @SaIgnore
    @Operation(summary = "管理员密码和短信验证码登录", description = "公开接口；username、password、smsCode 三项校验成功后返回 Token 字符串，使用 Authorization: Bearer <token>；存量初始账号仍须先改密")
    @Log("管理员短信验证码登录")
    public Result<String> smsLogin(@Valid @RequestBody AdminSmsLoginRequest request) {
        var account = service.authenticateWithCode(request.username(), request.password(), request.smsCode());
        service.recordLogin(account.getId());
        PetTownToken.ADMIN.login(account.getId());
        PetTownToken.ADMIN.getTokenSession().set(AdminAuthController.SESSION_VERSION, account.getSessionVersion());
        return Result.success(PetTownToken.ADMIN.getTokenValue());
    }

    @PostMapping("/public/captcha/login/{name}")
    @SaIgnore
    @Operation(summary = "生成管理员登录验证码", description = "公开接口；按账号查询手机号，生成六位数字、五分钟有效的管理员专用验证码；只写入 Redis，不返回验证码；当前尚未接入短信投递服务")
    public Result<Void> createLoginCode(@PathVariable @NotBlank @Size(max = 50) String name) {
        service.generateLoginCode(name);
        return Result.success();
    }

    @GetMapping("/self")
    @Operation(summary = "获取当前管理员基础信息", description = "需要管理员登录；返回 avatar、userName 和 authorities")
    @SecurityRequirement(name = "bearerAuth")
    public Result<AdminSelfResponse> self() {
        Long id = PetTownToken.ADMIN.getLoginIdAsLong();
        var account = service.getAccount(id);
        return Result.success(new AdminSelfResponse(account.getAvatar(), account.getUsername(),
                service.permissions(id).stream().sorted().toList()));
    }

    @GetMapping("/info")
    @Operation(summary = "获取当前管理员详细信息", description = "需要管理员登录；返回个人资料和角色，不包含密码或会话内部信息")
    @SecurityRequirement(name = "bearerAuth")
    public Result<AdminInfoResponse> info() {
        Long id = PetTownToken.ADMIN.getLoginIdAsLong();
        var role = service.roles(id).stream().findFirst()
                .map(AdminRoleResponse::from).orElse(null);
        return Result.success(AdminInfoResponse.from(service.getAccount(id), role));
    }
}
