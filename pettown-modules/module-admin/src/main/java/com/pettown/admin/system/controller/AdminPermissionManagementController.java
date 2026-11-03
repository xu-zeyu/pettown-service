package com.pettown.admin.system.controller;

import com.pettown.admin.auth.annotation.AdminCheckPermission;
import com.pettown.admin.system.dto.*;
import com.pettown.admin.system.vo.*;
import com.pettown.business.admin.service.AdminPermission;
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
@RequestMapping("/api/admin/permission")
@Tag(name = "管理员权限目录")
@SecurityRequirement(name = "bearerAuth")
public class AdminPermissionManagementController {
    private final AdminService service;

    public AdminPermissionManagementController(AdminService service) {
        this.service = service;
    }

    @PostMapping("/create")
    @Operation(summary = "创建权限", description = "需要 SUB_ADMIN；编码唯一，不支持通配符")
    @AdminCheckPermission(AdminPermission.SUB_ADMIN)
    @Log("创建权限")
    public Result<Void> create(@Valid @RequestBody AdminPermissionCreateRequest request) {
        service.createPermission(actor(), request.name(), request.code());
        return Result.success();
    }

    @PostMapping("/update")
    @Operation(summary = "更新权限", description = "需要 SUB_ADMIN；非 null 字段局部更新；内置编码不可修改，自定义编码修改时同步迁移角色绑定")
    @AdminCheckPermission(AdminPermission.SUB_ADMIN)
    @Log("更新权限")
    public Result<Void> update(@Valid @RequestBody AdminPermissionUpdateRequest request) {
        service.updatePermission(actor(), request.id(), request.name(), request.code());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除权限", description = "需要 SUB_ADMIN；内置权限不可删除，自定义权限删除时清理角色绑定，后续请求立即失效")
    @AdminCheckPermission(AdminPermission.SUB_ADMIN)
    @Log("删除权限")
    public Result<Void> delete(@PathVariable Long id) {
        service.deletePermission(actor(), id);
        return Result.success();
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询权限详情", description = "需要 SUB_ADMIN")
    @AdminCheckPermission(AdminPermission.SUB_ADMIN)
    public Result<AdminPermissionResponse> get(@PathVariable Long id) {
        return Result.success(AdminPermissionResponse.from(service.getPermission(id)));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询权限", description = "需要 SUB_ADMIN；返回 list、total，size=-1 查询全部")
    @AdminCheckPermission(AdminPermission.SUB_ADMIN)
    public Result<PageResult<AdminPermissionResponse>> page(@Valid @ModelAttribute PageParam request) {
        var page = service.listPermissions(request);
        return Result.success(new PageResult<>(page.getList().stream().map(AdminPermissionResponse::from).toList(), page.getTotal()));
    }

    @GetMapping("/list")
    @Operation(summary = "查询所有权限", description = "需要 SUB_ADMIN")
    @AdminCheckPermission(AdminPermission.SUB_ADMIN)
    public Result<List<AdminPermissionResponse>> list() {
        PageParam page = new PageParam();
        page.setSize(-1);
        return Result.success(service.listPermissions(page).getList().stream().map(AdminPermissionResponse::from).toList());
    }

    @PostMapping("/batchSync")
    @Operation(summary = "批量同步权限", description = "需要 SUB_ADMIN；仅补充不存在的编码，已有名称保持不变，不删除已有权限；相同编码只创建一次，全部校验及写入在同一事务完成")
    @AdminCheckPermission(AdminPermission.SUB_ADMIN)
    @Log("批量同步权限")
    public Result<Void> sync(@Valid @RequestBody AdminPermissionSyncRequest request) {
        service.syncPermissions(actor(), request.permissions().stream()
                .map(p -> new AdminService.PermissionDefinition(p.name(), p.code())).toList());
        return Result.success();
    }

    private Long actor() { return PetTownToken.ADMIN.getLoginIdAsLong(); }
}
