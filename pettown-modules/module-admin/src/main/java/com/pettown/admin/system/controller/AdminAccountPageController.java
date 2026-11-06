package com.pettown.admin.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pettown.admin.system.dto.AdminAccountSearchRequest;
import com.pettown.admin.system.vo.AccountResponse;
import com.pettown.business.admin.constant.AdminPermissionCodes;
import com.pettown.business.admin.service.AdminService;
import com.pettown.common.mybatis.PageResult;
import com.pettown.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** v1 管理端账号分页查询；独立路径仅暴露受保护的 GET。 */
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
}
