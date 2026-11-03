package com.pettown.business.admin.service;

import com.pettown.business.admin.entity.AdminAccount;
import com.pettown.business.admin.entity.AdminRole;
import com.pettown.common.mybatis.PageParam;
import com.pettown.common.mybatis.PageResult;
import java.util.List;
import java.util.Set;

public interface AdminService {
    void initializeSuperAdmin(String username, String password);
    AdminAccount authenticate(String username, String password);
    AdminAccount getAccount(Long id);
    Set<String> permissions(Long accountId);
    List<AdminRole> roles(Long accountId);
    List<Long> accountRoleIds(Long accountId);
    PageResult<AdminAccount> listAccounts(PageParam page, String keyword, Boolean enabled);
    AdminAccount createAccount(Long actorId, String username, String displayName, String password);
    void updateAccount(Long actorId, Long id, String displayName);
    void setAccountEnabled(Long actorId, Long id, boolean enabled);
    void replaceAccountRoles(Long actorId, Long id, List<Long> roleIds);
    void resetPassword(Long actorId, Long id, String password);
    void changePassword(Long id, String oldPassword, String newPassword);
    PageResult<AdminRole> listRoles(PageParam page, String keyword, Boolean enabled);
    AdminRole getRole(Long id);
    AdminRole createRole(Long actorId, String code, String name);
    void updateRole(Long actorId, Long id, String name, boolean enabled);
    void deleteRole(Long actorId, Long id);
    List<String> rolePermissions(Long roleId);
    void replaceRolePermissions(Long actorId, Long id, List<String> permissions);
}
