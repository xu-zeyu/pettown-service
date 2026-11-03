package com.pettown.business.admin.service;

import com.pettown.business.admin.entity.AdminAccount;
import com.pettown.business.admin.entity.AdminRole;
import com.pettown.business.admin.entity.AdminPermissionRecord;
import com.pettown.common.mybatis.PageParam;
import com.pettown.common.mybatis.PageResult;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface AdminService {
    void initializeSuperAdmin(String username, String password);
    void initializeSuperAdmin(String username, String password, String phone);
    AdminAccount authenticate(String username, String password);
    AdminAccount getAccount(Long id);
    Optional<AdminAccount> findAccount(Long id);
    Set<String> permissions(Long accountId);
    List<AdminRole> roles(Long accountId);
    List<Long> accountRoleIds(Long accountId);
    PageResult<AdminAccount> listAccounts(PageParam page, String keyword, Boolean enabled);
    AdminAccount createAccount(Long actorId, String username, String displayName, String password);
    AdminAccount createAccount(Long actorId, String username, String displayName, String password, String phone);
    void updateAccount(Long actorId, Long id, String displayName);
    void updateAccount(Long actorId, Long id, String displayName, String phone);
    void setAccountEnabled(Long actorId, Long id, boolean enabled);
    void replaceAccountRoles(Long actorId, Long id, List<Long> roleIds);
    void assignAccountRole(Long actorId, Long id, Long roleId);
    void deleteAccount(Long actorId, Long id);
    void resetPassword(Long actorId, Long id, String password);
    void changePassword(Long id, String oldPassword, String newPassword);
    PageResult<AdminRole> listRoles(PageParam page, String keyword, Boolean enabled);
    AdminRole getRole(Long id);
    AdminRole createRole(Long actorId, String code, String name);
    void updateRole(Long actorId, Long id, String name, boolean enabled);
    void deleteRole(Long actorId, Long id);
    List<String> rolePermissions(Long roleId);
    void replaceRolePermissions(Long actorId, Long id, List<String> permissions);
    void generateLoginCode(String username);
    AdminAccount authenticateWithCode(String username, String password, String smsCode);
    void recordLogin(Long id);
    AdminAccount createAdmin(Long actorId, String username, String password, String phone, String realName, Long roleId);
    AdminRole createNamedRole(Long actorId, String rname, String description);
    PageResult<AdminRole> listNamedRoles(PageParam page);
    void updateNamedRole(Long actorId, Long id, String rname, String description);
    void deleteNamedRole(Long actorId, Long id);
    PageResult<AdminPermissionRecord> listPermissions(PageParam page);
    AdminPermissionRecord getPermission(Long id);
    void createPermission(Long actorId, String name, String code);
    void updatePermission(Long actorId, Long id, String name, String code);
    void deletePermission(Long actorId, Long id);
    void syncPermissions(Long actorId, List<PermissionDefinition> permissions);
    List<AdminPermissionRecord> rolePermissionRecords(Long roleId);
    void replaceRolePermissionIds(Long actorId, Long roleId, List<Long> permissionIds);

    record PermissionDefinition(String name, String code) { }
}
