package com.pettown.admin.auth.controller;

import com.pettown.admin.system.dto.ChangePasswordRequest;
import com.pettown.admin.system.dto.LoginRequest;
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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/auth")
@Tag(name = "管理后台认证")
public class AdminAuthController {
    public static final String SESSION_VERSION = "adminSessionVersion";
    private final AdminService service;

    public AdminAuthController(AdminService service) {
        this.service = service;
    }

    @PostMapping("/login")
    @Operation(summary = "管理员账号密码登录", description = "公开接口；返回管理员 Token。初始账号或被重置密码的账号须先修改密码")
    @Log("管理员登录")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        var account = service.authenticate(request.username(), request.password());
        PetTownToken.ADMIN.login(account.getId());
        PetTownToken.ADMIN.getTokenSession().set(SESSION_VERSION, account.getSessionVersion());
        return Result.success(new LoginResponse("Bearer", PetTownToken.ADMIN.getTokenValue(),
                PetTownToken.ADMIN.getTokenTimeout(), AccountResponse.from(account)));
    }

    @PostMapping("/logout")
    @Operation(summary = "管理员退出登录", description = "只注销当前 Token，其他终端会话保持有效")
    @SecurityRequirement(name = "bearerAuth")
    @Log("管理员退出登录")
    public Result<Void> logout() {
        PetTownToken.ADMIN.logout();
        return Result.success();
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前管理员信息", description = "返回账号信息、已启用角色和有效权限；passwordChangeRequired 为 true 时须先改密")
    @SecurityRequirement(name = "bearerAuth")
    public Result<MeResponse> me() {
        Long id = PetTownToken.ADMIN.getLoginIdAsLong();
        return Result.success(new MeResponse(AccountResponse.from(service.getAccount(id)),
                service.roles(id).stream().map(RoleResponse::from).toList(),
                service.permissions(id).stream().sorted().toList()));
    }

    @GetMapping("/menus")
    @Operation(summary = "获取当前管理员菜单", description = "按当前有效读权限生成导航树，菜单不替代接口鉴权；初始密码未修改时拒绝访问")
    @SecurityRequirement(name = "bearerAuth")
    public Result<List<MenuResponse>> menus() {
        return Result.success(MenuResponse.visibleTo(service.permissions(PetTownToken.ADMIN.getLoginIdAsLong())));
    }

    @PutMapping("/password")
    @Operation(summary = "修改当前管理员密码", description = "校验原密码后更新密码，使该账号全部旧会话失效，成功后须重新登录")
    @SecurityRequirement(name = "bearerAuth")
    @Log("管理员修改密码")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        service.changePassword(PetTownToken.ADMIN.getLoginIdAsLong(), request.oldPassword(), request.newPassword());
        PetTownToken.ADMIN.logout();
        return Result.success();
    }
}
