package com.pettown.admin.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pettown.admin.system.dto.AdminPageRequest;
import com.pettown.admin.system.dto.RolePermissionsRequest;
import com.pettown.admin.system.dto.UpdateRoleRequest;
import com.pettown.admin.system.vo.RoleResponse;
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
@RequestMapping("/mng/system/roles")
@Tag(name = "角色管理")
@SecurityRequirement(name = "bearerAuth")
public class AdminRoleController {
    private final AdminService service;

    public AdminRoleController(AdminService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "分页查询管理员角色", description = "需要 system:role:read；按编码或名称模糊查询，size=-1 查询全部")
    @SaCheckPermission(value = AdminPermissionCodes.ROLE_READ, type = "admin")
    public Result<PageResult<RoleResponse>> list(@Valid @ModelAttribute AdminPageRequest request) {
        var page = service.listRoles(request, request.getKeyword(), request.getEnabled());
        return Result.success(new PageResult<>(page.getList().stream().map(RoleResponse::from).toList(), page.getTotal()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取管理员角色详情", description = "需要 system:role:read")
    @SaCheckPermission(value = AdminPermissionCodes.ROLE_READ, type = "admin")
    public Result<RoleResponse> get(@PathVariable Long id) {
        return Result.success(RoleResponse.from(service.getRole(id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改管理员角色", description = "需要 system:role:update；修改名称和启用状态，角色禁用后权限立即对后续请求失效")
    @SaCheckPermission(value = AdminPermissionCodes.ROLE_UPDATE, type = "admin")
    @Log("修改管理员角色")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
        service.updateRole(actor(), id, request.name(), request.enabled());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除管理员角色", description = "需要 system:role:delete；清空关联账号的 roleId 并保留账号，逻辑删除角色并清理角色权限，后续请求立即失去该角色权限")
    @SaCheckPermission(value = AdminPermissionCodes.ROLE_DELETE, type = "admin")
    @Log("删除管理员角色")
    public Result<Void> delete(@PathVariable Long id) {
        service.deleteRole(actor(), id);
        return Result.success();
    }

    @GetMapping("/{id}/permissions")
    @Operation(summary = "查询角色权限", description = "需要 system:role:read；返回绑定的权限编码数组")
    @SaCheckPermission(value = AdminPermissionCodes.ROLE_READ, type = "admin")
    public Result<List<String>> permissions(@PathVariable Long id) {
        return Result.success(service.rolePermissions(id));
    }

    @PutMapping("/{id}/permissions")
    @Operation(summary = "分配角色权限", description = "需要 system:role:assign-permission；整体替换，空数组清空，拒绝未知权限及超出自身范围的授权")
    @SaCheckPermission(value = AdminPermissionCodes.ROLE_ASSIGN_PERMISSION, type = "admin")
    @Log("分配管理员角色权限")
    public Result<Void> assignPermissions(@PathVariable Long id, @Valid @RequestBody RolePermissionsRequest request) {
        service.replaceRolePermissions(actor(), id, request.permissions());
        return Result.success();
    }

    private Long actor() {
        return PetTownToken.ADMIN.getLoginIdAsLong();
    }
}
