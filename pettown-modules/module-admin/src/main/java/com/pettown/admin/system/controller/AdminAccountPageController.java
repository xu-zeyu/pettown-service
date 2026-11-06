package com.pettown.admin.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pettown.admin.system.dto.AdminAccountSearchRequest;
import com.pettown.admin.system.dto.StatusRequest;
import com.pettown.admin.system.dto.V1CreateAccountRequest;
import com.pettown.admin.system.dto.V1ResetPasswordRequest;
import com.pettown.admin.system.vo.AccountResponse;
import com.pettown.business.admin.constant.AdminPermissionCodes;
import com.pettown.business.admin.service.AdminService;
import com.pettown.common.log.annotation.Log;
import com.pettown.common.mybatis.PageResult;
import com.pettown.common.result.Result;
import com.pettown.common.security.config.PetTownToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** v1 管理端账号接口；每个路由独立声明权限，不给旧控制器整体加别名。 */
@RestController
@RequestMapping("/api/admin/system/accounts")
@Tag(name = "管理员管理")
@SecurityRequirement(name = "bearerAuth")
public class AdminAccountPageController {
    private final AdminService service;

    public AdminAccountPageController(AdminService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "分页查询管理员账号", description = "需要 system:account:read；账号和显示名分别模糊匹配，状态及角色 ID 精确筛选，按 ID 升序")
    @SaCheckPermission(value = AdminPermissionCodes.ACCOUNT_READ, type = "admin")
    public Result<PageResult<AccountResponse>> list(@Valid @ModelAttribute AdminAccountSearchRequest request) {
        var page = service.searchAccounts(request, request.getUsername(), request.getDisplayName(),
                request.getEnabled(), request.getRoleId());
        return Result.success(new PageResult<>(page.getList().stream().map(AccountResponse::from).toList(),
                page.getTotal()));
    }

    @PostMapping
    @Operation(summary = "创建管理员账号", description = "需要 system:account:create；初始密码满足强度规则，首次登录必须改密，角色权限不得超出操作者；重复用户名返回业务码 4091")
    @SaCheckPermission(value = AdminPermissionCodes.ACCOUNT_CREATE, type = "admin")
    @Log("创建管理员账号")
    public Result<AccountResponse> create(@Valid @RequestBody V1CreateAccountRequest request) {
        return Result.success(AccountResponse.from(service.createV1Account(
                PetTownToken.ADMIN.getLoginIdAsLong(), request.username(), request.displayName(),
                request.password(), request.phone(), request.roleId())));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "启用或停用管理员账号", description = "需要 system:account:status；不能操作自己或超级管理员；停用后目标账号全部旧会话失效")
    @SaCheckPermission(value = AdminPermissionCodes.ACCOUNT_STATUS, type = "admin")
    @Log("启停管理员账号")
    public Result<Void> status(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        service.setAccountEnabled(PetTownToken.ADMIN.getLoginIdAsLong(), id, request.enabled());
        if (!request.enabled()) {
            PetTownToken.ADMIN.logout(id);
        }
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "软删除管理员账号", description = "需要 system:account:delete；不能删除自己或超级管理员；删除后目标账号不可登录，角色和权限保留")
    @SaCheckPermission(value = AdminPermissionCodes.ACCOUNT_DELETE, type = "admin")
    @Log("删除管理员账号")
    public Result<Void> delete(@PathVariable Long id) {
        service.deleteAccount(PetTownToken.ADMIN.getLoginIdAsLong(), id);
        PetTownToken.ADMIN.logout(id);
        return Result.success();
    }

    @PostMapping("/{id}/reset-password")
    @Operation(summary = "重置管理员密码", description = "需要 system:account:reset-password；重置后该账号旧会话失效，下次登录须先改密")
    @SaCheckPermission(value = AdminPermissionCodes.ACCOUNT_RESET_PASSWORD, type = "admin")
    @Log("重置管理员密码")
    public Result<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody V1ResetPasswordRequest request) {
        service.resetV1Password(PetTownToken.ADMIN.getLoginIdAsLong(), id, request.password());
        PetTownToken.ADMIN.logout(id);
        return Result.success();
    }
}
