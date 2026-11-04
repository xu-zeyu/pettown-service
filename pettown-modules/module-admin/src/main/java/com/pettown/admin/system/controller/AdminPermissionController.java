package com.pettown.admin.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pettown.admin.system.vo.PermissionResponse;
import com.pettown.business.admin.constant.AdminPermissionCodes;
import com.pettown.business.admin.service.AdminService;
import com.pettown.common.mybatis.PageParam;
import com.pettown.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mng/system/permissions")
@Tag(name = "管理后台权限点")
@SecurityRequirement(name = "bearerAuth")
public class AdminPermissionController {
    private final AdminService service;

    public AdminPermissionController(AdminService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "查询管理后台权限点", description = "需要 system:permission:read；返回数据库目录中的内置及自定义权限编码与名称；目录维护接口需要 SUB_ADMIN")
    @SaCheckPermission(value = AdminPermissionCodes.PERMISSION_READ, type = "admin")
    public Result<List<PermissionResponse>> list() {
        PageParam page = new PageParam();
        page.setSize(-1);
        return Result.success(service.listPermissions(page).getList().stream()
                .map(permission -> new PermissionResponse(permission.getCode(), permission.getName())).toList());
    }
}
