package com.pettown.business.admin.service.impl;

import com.pettown.business.admin.entity.AdminAccount;
import com.pettown.business.admin.entity.AdminPermissionRecord;
import com.pettown.business.admin.entity.AdminRole;
import com.pettown.business.admin.service.AdminAccountService;
import com.pettown.business.admin.service.AdminAuthService;
import com.pettown.business.admin.service.AdminPermissionService;
import com.pettown.business.admin.service.AdminRoleService;
import com.pettown.business.admin.service.AdminService;
import com.pettown.common.mybatis.PageParam;
import com.pettown.common.mybatis.PageResult;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * {@link AdminService} 门面实现：按账号、认证、角色、权限域委托给对应服务，
 * 自身不承载业务规则，保证各域职责单一。
 */
@Service
public class AdminServiceImpl implements AdminService {

    private final AdminAccountService accounts;
    private final AdminAuthService auth;
    private final AdminRoleService roles;
    private final AdminPermissionService permissions;
    private final AdminAccessPolicy policy;

    // 门面自身实现四个域接口，按类型注入会产生自我歧义，须显式限定到各域实现。
    public AdminServiceImpl(@Qualifier("adminAccountServiceImpl") AdminAccountService accounts,
            @Qualifier("adminAuthServiceImpl") AdminAuthService auth,
            @Qualifier("adminRoleServiceImpl") AdminRoleService roles,
            @Qualifier("adminPermissionServiceImpl") AdminPermissionService permissions,
            AdminAccessPolicy policy) {
        this.accounts = accounts;
        this.auth = auth;
        this.roles = roles;
        this.permissions = permissions;
        this.policy = policy;
    }

    @Override
    public void initializeSuperAdmin(String username, String password, String phone) {
        accounts.initializeSuperAdmin(username, password, phone);
    }

    @Override
    public AdminAccount getAccount(Long id) {
        return accounts.getAccount(id);
    }

    @Override
    public Optional<AdminAccount> findAccount(Long id) {
        return accounts.findAccount(id);
    }

    @Override
    public PageResult<AdminAccount> listAccounts(PageParam page, String keyword, Boolean enabled) {
        return accounts.listAccounts(page, keyword, enabled);
    }

    @Override
    public PageResult<AdminAccount> searchAccounts(PageParam page, String username, String displayName,
            Boolean enabled, Long roleId) {
        return accounts.searchAccounts(page, username, displayName, enabled, roleId);
    }

    @Override
    public AdminAccount createAccount(Long actorId, String username, String displayName, String password, String phone) {
        return accounts.createAccount(actorId, username, displayName, password, phone);
    }

    @Override
    public AdminAccount createV1Account(Long actorId, String username, String displayName, String password,
            String phone, Long roleId) {
        return accounts.createV1Account(actorId, username, displayName, password, phone, roleId);
    }

    @Override
    public AdminAccount createAdmin(Long actorId, String username, String password, String phone, String realName, Long roleId) {
        return accounts.createAdmin(actorId, username, password, phone, realName, roleId);
    }

    @Override
    public void updateAccount(Long actorId, Long id, String displayName, String phone) {
        accounts.updateAccount(actorId, id, displayName, phone);
    }

    @Override
    public void setAccountEnabled(Long actorId, Long id, boolean enabled) {
        accounts.setAccountEnabled(actorId, id, enabled);
    }

    @Override
    public void replaceAccountRoles(Long actorId, Long id, List<Long> roleIds) {
        accounts.replaceAccountRoles(actorId, id, roleIds);
    }

    @Override
    public void assignAccountRole(Long actorId, Long id, Long roleId) {
        accounts.assignAccountRole(actorId, id, roleId);
    }

    @Override
    public void deleteAccount(Long actorId, Long id) {
        accounts.deleteAccount(actorId, id);
    }

    @Override
    public void resetPassword(Long actorId, Long id, String password) {
        accounts.resetPassword(actorId, id, password);
    }

    @Override
    public void resetV1Password(Long actorId, Long id, String password) {
        accounts.resetV1Password(actorId, id, password);
    }

    @Override
    public void changePassword(Long id, String oldPassword, String newPassword) {
        accounts.changePassword(id, oldPassword, newPassword);
    }

    @Override
    public void changeV1Password(Long id, String oldPassword, String newPassword) {
        accounts.changeV1Password(id, oldPassword, newPassword);
    }

    @Override
    public void recordLogin(Long id) {
        accounts.recordLogin(id);
    }

    @Override
    public void generateLoginCode(String username) {
        auth.generateLoginCode(username);
    }

    @Override
    public AdminAccount authenticate(String username, String password) {
        return auth.authenticate(username, password);
    }

    @Override
    public AdminAccount authenticateWithCode(String username, String password, String smsCode) {
        return auth.authenticateWithCode(username, password, smsCode);
    }

    @Override
    public Set<String> permissions(Long accountId) {
        return policy.permissions(accountId);
    }

    @Override
    public List<AdminRole> roles(Long accountId) {
        return policy.roles(accountId);
    }

    @Override
    public List<Long> accountRoleIds(Long accountId) {
        return policy.accountRoleIds(accountId);
    }

    @Override
    public PageResult<AdminRole> listRoles(PageParam page, String keyword, Boolean enabled) {
        return roles.listRoles(page, keyword, enabled);
    }

    @Override
    public PageResult<AdminRole> listNamedRoles(PageParam page) {
        return roles.listNamedRoles(page);
    }

    @Override
    public AdminRole getRole(Long id) {
        return roles.getRole(id);
    }

    @Override
    public AdminRole createNamedRole(Long actorId, String rname, String description) {
        return roles.createNamedRole(actorId, rname, description);
    }

    @Override
    public void updateRole(Long actorId, Long id, String name, boolean enabled) {
        roles.updateRole(actorId, id, name, enabled);
    }

    @Override
    public void updateNamedRole(Long actorId, Long id, String rname, String description) {
        roles.updateNamedRole(actorId, id, rname, description);
    }

    @Override
    public void deleteRole(Long actorId, Long id) {
        roles.deleteRole(actorId, id);
    }

    @Override
    public void deleteNamedRole(Long actorId, Long id) {
        roles.deleteNamedRole(actorId, id);
    }

    @Override
    public List<String> rolePermissions(Long roleId) {
        return roles.rolePermissions(roleId);
    }

    @Override
    public List<AdminPermissionRecord> rolePermissionRecords(Long roleId) {
        return roles.rolePermissionRecords(roleId);
    }

    @Override
    public void replaceRolePermissions(Long actorId, Long roleId, List<String> permissionCodes) {
        roles.replaceRolePermissions(actorId, roleId, permissionCodes);
    }

    @Override
    public void replaceRolePermissionIds(Long actorId, Long roleId, List<Long> permissionIds) {
        roles.replaceRolePermissionIds(actorId, roleId, permissionIds);
    }

    @Override
    public PageResult<AdminPermissionRecord> listPermissions(PageParam page) {
        return permissions.listPermissions(page);
    }

    @Override
    public AdminPermissionRecord getPermission(Long id) {
        return permissions.getPermission(id);
    }

    @Override
    public void createPermission(Long actorId, String name, String code) {
        permissions.createPermission(actorId, name, code);
    }

    @Override
    public void updatePermission(Long actorId, Long id, String name, String code) {
        permissions.updatePermission(actorId, id, name, code);
    }

    @Override
    public void deletePermission(Long actorId, Long id) {
        permissions.deletePermission(actorId, id);
    }

    @Override
    public void syncPermissions(Long actorId, List<PermissionDefinition> definitions) {
        permissions.syncPermissions(actorId, definitions);
    }
}
