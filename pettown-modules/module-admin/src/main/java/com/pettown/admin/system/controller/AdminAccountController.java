package com.pettown.admin.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pettown.admin.system.dto.AccountRoleRequest;
import com.pettown.admin.system.dto.AccountRolesRequest;
import com.pettown.admin.system.dto.AdminPageRequest;
import com.pettown.admin.system.dto.CreateAccountRequest;
import com.pettown.admin.system.dto.ResetPasswordRequest;
import com.pettown.admin.system.dto.StatusRequest;
import com.pettown.admin.system.dto.UpdateAccountRequest;
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
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/mng/system/accounts")
@Tag(name = "管理员管理")
@SecurityRequirement(name = "bearerAuth")
public class AdminAccountController {
    private final AdminService service;

    public AdminAccountController(AdminService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "分页查询管理员", description = "需要 system:account:read；按账号或显示名模糊查询，size=-1 查询全部")
    @SaCheckPermission(value = AdminPermissionCodes.ACCOUNT_READ, type = "admin")
    public Result<PageResult<AccountResponse>> list(@Valid @ModelAttribute AdminPageRequest request) {
        var page = service.listAccounts(request, request.getKeyword(), request.getEnabled());
        return Result.success(new PageResult<>(page.getList().stream().map(AccountResponse::from).toList(), page.getTotal()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取管理员详情", description = "需要 system:account:read；响应不包含密码哈希和会话版本")
    @SaCheckPermission(value = AdminPermissionCodes.ACCOUNT_READ, type = "admin")
    public Result<AccountResponse> get(@PathVariable Long id) {
        return Result.success(AccountResponse.from(service.getAccount(id)));
    }

    @PostMapping
    @Operation(summary = "创建管理员", description = "需要 system:account:create；默认启用、无角色，首次登录须修改密码")
    @SaCheckPermission(value = AdminPermissionCodes.ACCOUNT_CREATE, type = "admin")
    @Log("创建管理员")
    public Result<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        return Result.success(AccountResponse.from(service.createAccount(actor(),
                request.username(), request.displayName(), request.password(), request.phone())));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改管理员资料", description = "需要 system:account:update；修改显示名及可选手机号，手机号变更使旧会话失效；不能修改自身、超级管理员或权限超出自身范围的账号")
    @SaCheckPermission(value = AdminPermissionCodes.ACCOUNT_UPDATE, type = "admin")
    @Log("修改管理员")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateAccountRequest request) {
        service.updateAccount(actor(), id, request.displayName(), request.phone());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "软删除管理员", description = "需要 system:account:delete；软删除账号并注销其全部会话，保留角色及角色权限；不能删除自身、超级管理员或权限超出自身范围的账号")
    @SaCheckPermission(value = AdminPermissionCodes.ACCOUNT_DELETE, type = "admin")
    @Log("删除管理员")
    public Result<Void> delete(@PathVariable Long id) {
        service.deleteAccount(actor(), id);
        PetTownToken.ADMIN.logout(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "启用或禁用管理员", description = "需要 system:account:status；更新状态使全部旧会话失效，重新启用也不会恢复旧 Token")
    @SaCheckPermission(value = AdminPermissionCodes.ACCOUNT_STATUS, type = "admin")
    @Log("修改管理员状态")
    public Result<Void> status(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        service.setAccountEnabled(actor(), id, request.enabled());
        return Result.success();
    }

    @GetMapping("/{id}/roles")
    @Operation(summary = "查询管理员角色绑定（兼容接口）", description = "需要 system:account:read；返回空数组或单项角色 ID 数组，包含已禁用角色；新客户端使用 /{id}/role", deprecated = true)
    @SaCheckPermission(value = AdminPermissionCodes.ACCOUNT_READ, type = "admin")
    public Result<List<Long>> roles(@PathVariable Long id) {
        service.getAccount(id);
        return Result.success(service.accountRoleIds(id));
    }

    @PutMapping("/{id}/roles")
    @Operation(summary = "分配管理员角色（兼容接口）", description = "需要 system:account:assign-role；最多一项，空数组解除关联，多个角色会被拒绝；不能越权授权，新客户端使用 /{id}/role", deprecated = true)
    @SaCheckPermission(value = AdminPermissionCodes.ACCOUNT_ASSIGN_ROLE, type = "admin")
    @Log("分配管理员角色")
    public Result<Void> assignRoles(@PathVariable Long id, @Valid @RequestBody AccountRolesRequest request) {
        service.replaceAccountRoles(actor(), id, request.roleIds());
        return Result.success();
    }

    @GetMapping("/{id}/role")
    @Operation(summary = "查询管理员关联角色", description = "需要 system:account:read；返回账号的 roleId，未关联时为 null，包含已禁用角色")
    @SaCheckPermission(value = AdminPermissionCodes.ACCOUNT_READ, type = "admin")
    public Result<Long> role(@PathVariable Long id) {
        return Result.success(service.getAccount(id).getRoleId());
    }

    @PutMapping("/{id}/role")
    @Operation(summary = "设置管理员关联角色", description = "需要 system:account:assign-role；一个账号最多一个角色，roleId 为 null 或不传时解除关联，后续请求立即生效；不能修改自身、超级管理员或越权授权")
    @SaCheckPermission(value = AdminPermissionCodes.ACCOUNT_ASSIGN_ROLE, type = "admin")
    @Log("设置管理员角色")
    public Result<Void> assignRole(@PathVariable Long id, @Valid @RequestBody AccountRoleRequest request) {
        service.assignAccountRole(actor(), id, request.roleId());
        return Result.success();
    }

    @PutMapping("/{id}/password")
    @Operation(summary = "重置管理员密码", description = "需要 system:account:reset-password；使全部旧会话失效，下次登录必须修改密码")
    @SaCheckPermission(value = AdminPermissionCodes.ACCOUNT_RESET_PASSWORD, type = "admin")
    @Log("重置管理员密码")
    public Result<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        service.resetPassword(actor(), id, request.password());
        return Result.success();
    }

    private Long actor() {
        return PetTownToken.ADMIN.getLoginIdAsLong();
    }
}
