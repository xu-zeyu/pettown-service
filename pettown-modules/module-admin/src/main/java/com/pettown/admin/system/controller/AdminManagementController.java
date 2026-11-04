package com.pettown.admin.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pettown.admin.system.dto.AdminCreateRequest;
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
@RequestMapping("/mng")
@Tag(name = "管理员账号管理")
@SecurityRequirement(name = "bearerAuth")
public class AdminManagementController {
    private final AdminService service;

    public AdminManagementController(AdminService service) {
        this.service = service;
    }

    @PostMapping("/create")
    @Operation(summary = "创建管理员", description = "需要 SUB_ADMIN。支持 phone、realName、roleId。密码以 SHA-256 哈希保存，创建的账号无需首次改密。授予的权限必须在操作者自身权限范围内。")
    @SaCheckPermission(value = AdminPermissionCodes.SUB_ADMIN, type = "admin")
    @Log("创建子管理员")
    public Result<Void> create(@Valid @RequestBody AdminCreateRequest request) {
        service.createAdmin(actor(), request.username(), request.password(), request.phone(), request.realName(), request.roleId());
        return Result.success();
    }

    private Long actor() {
        return PetTownToken.ADMIN.getLoginIdAsLong();
    }
}
