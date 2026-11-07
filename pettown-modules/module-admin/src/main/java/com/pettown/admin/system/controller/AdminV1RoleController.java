package com.pettown.admin.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pettown.admin.system.dto.AdminPageRequest;
import com.pettown.admin.system.dto.RolePermissionsRequest;
import com.pettown.admin.system.dto.V1CreateRoleRequest;
import com.pettown.admin.system.dto.V1UpdateRoleRequest;
import com.pettown.admin.system.vo.V1RoleResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** v1 管理端角色接口；每项操作按其权限码独立保护。 */
@RestController
@RequestMapping("/api/admin/system/roles")
@Tag(name = "管理员角色管理")
@SecurityRequirement(name = "bearerAuth")
public class AdminV1RoleController {
    private final AdminService service;

    public AdminV1RoleController(AdminService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "分页查询角色", description = "需要 system:role:read；按编码或名称模糊筛选，可按启用状态筛选，按 ID 升序")
    @SaCheckPermission(value = AdminPermissionCodes.ROLE_READ, type = "admin")
    public Result<PageResult<V1RoleResponse>> list(@Valid @ModelAttribute AdminPageRequest request) {
        var page = service.listRoles(request, request.getKeyword(), request.getEnabled());
        return Result.success(new PageResult<>(page.getList().stream().map(V1RoleResponse::from).toList(),
                page.getTotal()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询角色详情", description = "需要 system:role:read；不返回已软删角色")
    @SaCheckPermission(value = AdminPermissionCodes.ROLE_READ, type = "admin")
    public Result<V1RoleResponse> get(@PathVariable Long id) {
        return Result.success(V1RoleResponse.from(service.getRole(id)));
    }

    @GetMapping("/{id}/permissions")
    @Operation(summary = "查询角色权限码集合", description = "需要 system:role:assign-permission；返回该角色当前绑定的权限编码")
    @SaCheckPermission(value = AdminPermissionCodes.ROLE_ASSIGN_PERMISSION, type = "admin")
    public Result<List<String>> rolePermissions(@PathVariable Long id) {
        return Result.success(service.rolePermissions(id));
    }

    @PutMapping("/{id}/permissions")
    @Operation(summary = "同步角色权限码集合", description = "需要 system:role:assign-permission；整体替换、空数组清空；仅接受权限目录内编码（非法编码整体拒绝），且只能授予操作者持有的权限，变更即时生效")
    @SaCheckPermission(value = AdminPermissionCodes.ROLE_ASSIGN_PERMISSION, type = "admin")
    @Log("分配角色权限")
    public Result<Void> replaceRolePermissions(@PathVariable Long id, @Valid @RequestBody RolePermissionsRequest request) {
        service.replaceRolePermissions(PetTownToken.ADMIN.getLoginIdAsLong(), id, request.permissions());
        return Result.success();
    }

    @PostMapping
    @Operation(summary = "创建角色", description = "需要 system:role:create；角色编码唯一、创建后不可修改，默认启用")
    @SaCheckPermission(value = AdminPermissionCodes.ROLE_CREATE, type = "admin")
    @Log("创建管理员角色")
    public Result<V1RoleResponse> create(@Valid @RequestBody V1CreateRoleRequest request) {
        return Result.success(V1RoleResponse.from(service.createV1Role(
                actor(), request.code(), request.name(), request.description())));
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑角色", description = "需要 system:role:update；更新名称、说明及启用状态，编码不变")
    @SaCheckPermission(value = AdminPermissionCodes.ROLE_UPDATE, type = "admin")
    @Log("编辑管理员角色")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody V1UpdateRoleRequest request) {
        service.updateV1Role(actor(), id, request.name(), request.description(), request.enabled());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "软删除角色", description = "需要 system:role:delete；仍被未软删账号引用时拒绝，并返回引用数量")
    @SaCheckPermission(value = AdminPermissionCodes.ROLE_DELETE, type = "admin")
    @Log("删除管理员角色")
    public Result<Void> delete(@PathVariable Long id) {
        service.deleteV1Role(actor(), id);
        return Result.success();
    }

    private Long actor() {
        return PetTownToken.ADMIN.getLoginIdAsLong();
    }
}
