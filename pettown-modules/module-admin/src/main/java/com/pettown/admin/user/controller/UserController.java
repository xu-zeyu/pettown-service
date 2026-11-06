package com.pettown.admin.user.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.pettown.admin.user.dto.UserStatusRequest;
import com.pettown.admin.user.vo.AdminUserResponse;
import com.pettown.business.admin.constant.AdminPermissionCodes;
import com.pettown.business.admin.service.AdminUserService;
import com.pettown.common.commonEnum.UserSourceEnum;
import com.pettown.common.log.annotation.Log;
import com.pettown.common.mybatis.PageParam;
import com.pettown.common.mybatis.PageResult;
import com.pettown.common.result.Result;
import com.pettown.common.security.config.PetTownToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "C 端用户管理")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    private final AdminUserService service;

    public UserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "分页查询 C 端用户", description = "需要 biz:user:read；手机号模糊匹配，注册来源精确匹配；按注册时间、ID 倒序")
    @SaCheckPermission(value = AdminPermissionCodes.USER_READ, type = "admin")
    public Result<PageResult<AdminUserResponse>> list(@Valid @ModelAttribute PageParam page,
            @RequestParam(required = false) String phone, @RequestParam(required = false) UserSourceEnum source) {
        var result = service.listUsers(page, phone, source);
        return Result.success(new PageResult<>(result.getList().stream().map(AdminUserResponse::from).toList(),
                result.getTotal()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询 C 端用户详情", description = "需要 biz:user:read；不返回密码散列")
    @SaCheckPermission(value = AdminPermissionCodes.USER_READ, type = "admin")
    public Result<AdminUserResponse> get(@PathVariable String id) {
        return Result.success(AdminUserResponse.from(service.getUser(id)));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "封禁或解封 C 端用户", description = "需要 biz:user:status；封禁后小程序旧 Token 失效且登录被拒；解封需重新登录")
    @SaCheckPermission(value = AdminPermissionCodes.USER_STATUS, type = "admin")
    @Log("修改 C 端用户状态")
    public Result<Void> status(@PathVariable String id, @Valid @RequestBody UserStatusRequest request) {
        service.setUserStatus(PetTownToken.ADMIN.getLoginIdAsLong(), id, request.status());
        return Result.success();
    }
}
