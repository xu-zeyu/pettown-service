package com.pettown.admin.auth.controller;

import com.pettown.admin.system.vo.AccountResponse;
import com.pettown.admin.system.vo.MeResponse;
import com.pettown.admin.system.vo.MenuResponse;
import com.pettown.admin.system.vo.RoleResponse;
import com.pettown.business.admin.service.AdminService;
import com.pettown.common.result.Result;
import com.pettown.common.security.config.PetTownToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** TAPD 管理端当前用户接口；仅开放 me 与 menus 两个受保护的 GET 路由。 */
@RestController
@RequestMapping("/api/admin/auth")
@Tag(name = "管理后台当前用户")
public class AdminCurrentAuthController {
    private final AdminService service;

    public AdminCurrentAuthController(AdminService service) {
        this.service = service;
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前管理员信息", description = "返回实时账号、已启用角色和有效权限；初始密码未修改时可访问")
    @SecurityRequirement(name = "bearerAuth")
    public Result<MeResponse> me() {
        Long id = PetTownToken.ADMIN.getLoginIdAsLong();
        return Result.success(new MeResponse(AccountResponse.from(service.getAccount(id)),
                service.roles(id).stream().map(RoleResponse::from).toList(),
                service.permissions(id).stream().sorted().toList()));
    }

    @GetMapping("/menus")
    @Operation(summary = "获取当前管理员菜单", description = "按当前有效读权限生成导航树；初始密码未修改时拒绝访问")
    @SecurityRequirement(name = "bearerAuth")
    public Result<List<MenuResponse>> menus() {
        Long id = PetTownToken.ADMIN.getLoginIdAsLong();
        return Result.success(MenuResponse.visibleTo(service.permissions(id)));
    }
}
