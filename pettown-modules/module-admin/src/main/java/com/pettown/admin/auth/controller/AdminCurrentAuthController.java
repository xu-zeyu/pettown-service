package com.pettown.admin.auth.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import com.pettown.admin.auth.service.AdminLoginService;
import com.pettown.admin.system.dto.V1ChangePasswordRequest;
import com.pettown.admin.system.dto.V1LoginRequest;
import com.pettown.admin.system.vo.AccountResponse;
import com.pettown.admin.system.vo.LoginResponse;
import com.pettown.admin.system.vo.MeResponse;
import com.pettown.admin.system.vo.MenuResponse;
import com.pettown.admin.system.vo.RoleResponse;
import com.pettown.business.admin.service.AdminService;
import com.pettown.common.log.annotation.Log;
import com.pettown.common.result.Result;
import com.pettown.common.security.config.PetTownToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** TAPD 管理端当前用户接口；仅暴露受保护的本人信息、菜单与改密路由。 */
@RestController
@RequestMapping("/api/admin/auth")
@Tag(name = "管理后台当前用户")
public class AdminCurrentAuthController {
    private final AdminService service;
    private final AdminLoginService loginService;

    public AdminCurrentAuthController(AdminService service, AdminLoginService loginService) {
        this.service = service;
        this.loginService = loginService;
    }

    @SaIgnore
    @PostMapping("/login")
    @Operation(summary = "管理员登录（图形验证码）", description = "公开接口；username+password+图形验证码，连续 5 次密码失败锁定 30 分钟；返回 token 与 passwordChangeRequired")
    public Result<LoginResponse> login(@Valid @RequestBody V1LoginRequest request) {
        return Result.success(loginService.login(request.username(), request.password(),
                request.captchaId(), request.captchaCode()));
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前管理员信息", description = "返回实时账号、已启用角色和有效权限；初始密码未修改时可访问")
    @SecurityRequirement(name = "bearerAuth")
    public Result<MeResponse> me() {
        Long id = PetTownToken.ADMIN.getLoginIdAsLong();
        return Result.success(new MeResponse(AccountResponse.from(service.getAccount(id)),
                service.roles(id).stream().map(RoleResponse::from).toList(),
                service.permissions(id).stream().sorted().toList()));
    }

    @GetMapping("/menus")
    @Operation(summary = "获取当前管理员菜单", description = "按当前有效读权限生成导航树；初始密码未修改时拒绝访问")
    @SecurityRequirement(name = "bearerAuth")
    public Result<List<MenuResponse>> menus() {
        Long id = PetTownToken.ADMIN.getLoginIdAsLong();
        return Result.success(MenuResponse.visibleTo(service.permissions(id)));
    }

    @PostMapping("/change-password")
    @Operation(summary = "修改本人管理员密码", description = "校验原密码；新密码须为 8–64 位且含字母、数字；成功后全部旧会话失效，须重新登录")
    @SecurityRequirement(name = "bearerAuth")
    @Log("管理员修改密码")
    public Result<Void> changePassword(@Valid @RequestBody V1ChangePasswordRequest request) {
        Long id = PetTownToken.ADMIN.getLoginIdAsLong();
        service.changeV1Password(id, request.oldPassword(), request.newPassword());
        PetTownToken.ADMIN.logout(id);
        return Result.success();
    }
}
