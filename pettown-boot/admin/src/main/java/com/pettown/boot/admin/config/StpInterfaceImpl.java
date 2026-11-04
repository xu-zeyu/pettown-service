package com.pettown.boot.admin.config;

import cn.dev33.satoken.stp.StpInterface;
import com.pettown.business.admin.entity.AdminAccount;
import com.pettown.business.admin.entity.AdminRole;
import com.pettown.business.admin.service.AdminService;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 实现 Sa-Token 鉴权数据提供：通过 AdminService 实时读取管理员角色与权限。
 * 超级管理员服务端以 {@code *} 表示全权限；响应中的权限列表仍返回具体编码。
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    private final AdminService adminService;

    public StpInterfaceImpl(AdminService adminService) {
        this.adminService = adminService;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        if (!"admin".equals(loginType)) {
            return List.of();
        }
        Long accountId = Long.valueOf(loginId.toString());
        AdminAccount account = adminService.findAccount(accountId).orElse(null);
        if (account == null || !Boolean.TRUE.equals(account.getEnabled())) {
            return List.of();
        }
        if (Boolean.TRUE.equals(account.getSuperAdmin())) {
            return List.of("*");
        }
        return adminService.permissions(accountId).stream().sorted().toList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        if (!"admin".equals(loginType)) {
            return List.of();
        }
        return adminService.roles(Long.valueOf(loginId.toString())).stream()
                .map(AdminRole::getCode)
                .toList();
    }
}
