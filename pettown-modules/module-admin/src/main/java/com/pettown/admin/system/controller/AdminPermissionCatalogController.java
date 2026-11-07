package com.pettown.admin.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pettown.admin.system.vo.PermissionResponse;
import com.pettown.business.admin.constant.AdminPermissionCodes;
import com.pettown.business.admin.service.AdminService;
import com.pettown.common.log.annotation.Log;
import com.pettown.common.mybatis.PageParam;
import com.pettown.common.result.Result;
import com.pettown.common.security.config.PetTownToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** v1 管理端权限目录：内置编码与 docs/sql/admin.sql 对齐，sync 幂等补入缺失编码。 */
@RestController
@RequestMapping("/api/admin/system/permissions")
@Tag(name = "权限点目录")
@SecurityRequirement(name = "bearerAuth")
public class AdminPermissionCatalogController {
    private final AdminService service;

    public AdminPermissionCatalogController(AdminService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "查询权限点目录", description = "需要 system:permission:read；返回内置及自定义权限编码与名称，与 AdminPermissionCodes 内置常量对齐")
    @SaCheckPermission(value = AdminPermissionCodes.PERMISSION_READ, type = "admin")
    public Result<List<PermissionResponse>> list() {
        PageParam page = new PageParam();
        page.setSize(-1);
        return Result.success(service.listPermissions(page).getList().stream()
                .map(record -> new PermissionResponse(record.getCode(), record.getName())).toList());
    }

    @PostMapping("/sync")
    @Operation(summary = "同步内置权限码到目录", description = "需要 SUB_ADMIN；将 AdminPermissionCodes 声明的编码幂等补入目录，只新增缺失项、不覆盖名称、不删除旧项，可重复执行")
    @SaCheckPermission(value = AdminPermissionCodes.SUB_ADMIN, type = "admin")
    @Log("同步权限目录")
    public Result<Void> sync() {
        service.syncBuiltinPermissions(PetTownToken.ADMIN.getLoginIdAsLong());
        return Result.success();
    }
}
