package com.pettown.admin.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pettown.admin.system.dto.AdminRoleAssignPermissionRequest;
import com.pettown.admin.system.dto.AdminRoleCreateRequest;
import com.pettown.admin.system.dto.AdminRoleDeleteRequest;
import com.pettown.admin.system.dto.AdminRoleUpdateRequest;
import com.pettown.admin.system.vo.AdminPermissionResponse;
import com.pettown.admin.system.vo.AdminRoleResponse;
import com.pettown.business.admin.constant.AdminPermissionCodes;
import com.pettown.business.admin.service.AdminService;
import com.pettown.common.log.annotation.Log;
import com.pettown.common.mybatis.PageParam;
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
@RequestMapping("/mng/role")
@Tag(name = "管理员角色管理")
@SecurityRequirement(name = "bearerAuth")
public class AdminRoleManagementController {
    private final AdminService service;

    public AdminRoleManagementController(AdminService service) {
        this.service = service;
    }

    @PostMapping("/create")
    @Operation(summary = "创建角色", description = "需要 SUB_ADMIN；以 rname、description 创建，名称不可与未删除角色重复")
    @SaCheckPermission(value = AdminPermissionCodes.SUB_ADMIN, type = "admin")
    @Log("创建角色")
    public Result<Void> create(@Valid @RequestBody AdminRoleCreateRequest request) {
        service.createNamedRole(actor(), request.rname(), request.description());
        return Result.success();
    }

    @PostMapping("/update")
    @Operation(summary = "更新角色", description = "需要 SUB_ADMIN；只更新非 null 的 rname、description，不允许修改超出自身权限范围的角色")
    @SaCheckPermission(value = AdminPermissionCodes.SUB_ADMIN, type = "admin")
    @Log("更新角色")
    public Result<Void> update(@Valid @RequestBody AdminRoleUpdateRequest request) {
        service.updateNamedRole(actor(), request.id(), request.rname(), request.description());
        return Result.success();
    }

    @PostMapping("/delete")
    @Operation(summary = "删除角色", description = "需要 SUB_ADMIN；逻辑删除并清理角色权限及账号绑定，后续请求立即失去该角色的权限")
    @SaCheckPermission(value = AdminPermissionCodes.SUB_ADMIN, type = "admin")
    @Log("删除角色")
    public Result<Void> delete(@Valid @RequestBody AdminRoleDeleteRequest request) {
        service.deleteNamedRole(actor(), request.id());
        return Result.success();
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询角色详情", description = "需要 SUB_ADMIN；不返回已删除角色")
    @SaCheckPermission(value = AdminPermissionCodes.SUB_ADMIN, type = "admin")
    public Result<AdminRoleResponse> get(@PathVariable Long id) {
        return Result.success(AdminRoleResponse.from(service.getRole(id)));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询角色", description = "需要 SUB_ADMIN；返回 list、total，size=-1 查询全部")
    @SaCheckPermission(value = AdminPermissionCodes.SUB_ADMIN, type = "admin")
    public Result<PageResult<AdminRoleResponse>> page(@Valid @ModelAttribute PageParam request) {
        var page = service.listNamedRoles(request);
        return Result.success(new PageResult<>(page.getList().stream().map(AdminRoleResponse::from).toList(), page.getTotal()));
    }

    @GetMapping("/list")
    @Operation(summary = "查询所有角色", description = "需要 SUB_ADMIN；只返回未删除角色")
    @SaCheckPermission(value = AdminPermissionCodes.SUB_ADMIN, type = "admin")
    public Result<List<AdminRoleResponse>> list() {
        PageParam page = new PageParam();
        page.setSize(-1);
        return Result.success(service.listNamedRoles(page).getList().stream().map(AdminRoleResponse::from).toList());
    }

    @GetMapping("/{id}/permissions")
    @Operation(summary = "查询角色权限列表", description = "需要 SUB_ADMIN；返回权限 ID、name、code")
    @SaCheckPermission(value = AdminPermissionCodes.SUB_ADMIN, type = "admin")
    public Result<List<AdminPermissionResponse>> permissions(@PathVariable Long id) {
        return Result.success(service.rolePermissionRecords(id).stream().map(AdminPermissionResponse::from).toList());
    }

    @PostMapping("/assignPermissions")
    @Operation(summary = "为角色分配权限", description = "需要 SUB_ADMIN；permissionIds 整体替换，空数组清空、重复 ID 去重；拒绝不存在的权限和越权授权，后续请求立即生效")
    @SaCheckPermission(value = AdminPermissionCodes.SUB_ADMIN, type = "admin")
    @Log("分配角色权限")
    public Result<Void> assign(@Valid @RequestBody AdminRoleAssignPermissionRequest request) {
        service.replaceRolePermissionIds(actor(), request.roleId(), request.permissionIds());
        return Result.success();
    }

    private Long actor() {
        return PetTownToken.ADMIN.getLoginIdAsLong();
    }
}
