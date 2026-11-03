package com.pettown.admin.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pettown.admin.system.vo.PermissionResponse;
import com.pettown.business.admin.service.AdminPermission;
import com.pettown.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/system/permissions")
@Tag(name = "管理后台权限点")
@SecurityRequirement(name = "bearerAuth")
public class AdminPermissionController {
    @GetMapping
    @Operation(summary = "查询管理后台权限点", description = "需要 system:permission:read；返回由代码维护的权限编码与名称，不支持动态创建权限点")
    @SaCheckPermission(type = "admin", value = "system:permission:read")
    public Result<List<PermissionResponse>> list() {
        return Result.success(Arrays.stream(AdminPermission.values())
                .map(permission -> new PermissionResponse(permission.code(), permission.title())).toList());
    }
}
