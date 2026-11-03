package com.pettown.business.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pettown.business.admin.entity.AdminAccount;
import com.pettown.business.admin.entity.AdminAccountRole;
import com.pettown.business.admin.entity.AdminRole;
import com.pettown.business.admin.entity.AdminRolePermission;
import com.pettown.business.admin.mapper.AdminAccountMapper;
import com.pettown.business.admin.mapper.AdminAccountRoleMapper;
import com.pettown.business.admin.mapper.AdminRoleMapper;
import com.pettown.business.admin.mapper.AdminRolePermissionMapper;
import com.pettown.business.admin.service.AdminPermission;
import com.pettown.business.admin.service.AdminService;
import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;
import com.pettown.common.mybatis.PageParam;
import com.pettown.common.mybatis.PageResult;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminServiceImpl implements AdminService {
    private final AdminAccountMapper accounts;
    private final AdminRoleMapper roleMapper;
    private final AdminAccountRoleMapper accountRoles;
    private final AdminRolePermissionMapper rolePermissions;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final String dummyHash = encoder.encode("invalid-account-password");

    public AdminServiceImpl(AdminAccountMapper accounts, AdminRoleMapper roleMapper,
                            AdminAccountRoleMapper accountRoles, AdminRolePermissionMapper rolePermissions) {
        this.accounts = accounts;
        this.roleMapper = roleMapper;
        this.accountRoles = accountRoles;
        this.rolePermissions = rolePermissions;
    }

    @Override
    @Transactional
    public void initializeSuperAdmin(String username, String password) {
        if (accounts.selectById(1L) != null) {
            return;
        }
        validateUsername(username);
        validatePassword(password);
        AdminAccount root = newAccount(username, "超级管理员", password);
        root.setId(1L);
        root.setSuperAdmin(true);
        accounts.insert(root);
    }

    @Override
    public AdminAccount authenticate(String username, String password) {
        AdminAccount account = accounts.selectOne(AdminAccount::getUsername, username);
        boolean matches = encoder.matches(password, account == null ? dummyHash : account.getPasswordHash());
        if (!matches || account == null || !Boolean.TRUE.equals(account.getEnabled())) {
            throw failure("账号或密码错误");
        }
        return account;
    }

    @Override
    public AdminAccount getAccount(Long id) {
        return accounts.selectByIdOpt(id).orElseThrow(() ->
                new BusinessException(ErrorCodeEnum.NOT_FOUND, "管理员不存在"));
    }

    @Override
    public Set<String> permissions(Long accountId) {
        AdminAccount account = getAccount(accountId);
        if (!Boolean.TRUE.equals(account.getEnabled())) {
            return Set.of();
        }
        if (Boolean.TRUE.equals(account.getSuperAdmin())) {
            return AdminPermission.codes();
        }
        return permissionsForRoles(roles(accountId).stream().map(AdminRole::getId).toList());
    }

    @Override
    public List<AdminRole> roles(Long accountId) {
        return roleMapper.selectList(AdminRole::getId, accountRoleIds(accountId)).stream()
                .filter(role -> Boolean.TRUE.equals(role.getEnabled())).toList();
    }

    @Override
    public List<Long> accountRoleIds(Long accountId) {
        return accountRoles.selectList(AdminAccountRole::getAccountId, accountId).stream()
                .map(AdminAccountRole::getRoleId).sorted().toList();
    }

    private Set<String> permissionsForRoles(List<Long> roleIds) {
        return rolePermissions.selectList(AdminRolePermission::getRoleId, roleIds).stream()
                .map(AdminRolePermission::getPermissionCode).filter(AdminPermission.codes()::contains)
                .collect(Collectors.toSet());
    }

    @Override
    public PageResult<AdminAccount> listAccounts(PageParam page, String keyword, Boolean enabled) {
        var query = new LambdaQueryWrapper<AdminAccount>()
                .eq(enabled != null, AdminAccount::getEnabled, enabled);
        if (keyword != null && !keyword.isBlank()) {
            query.and(q -> q.like(AdminAccount::getUsername, keyword.trim())
                    .or().like(AdminAccount::getDisplayName, keyword.trim()));
        }
        return PageResult.from(accounts.selectPage(page, query.orderByAsc(AdminAccount::getId)));
    }

    @Override
    @Transactional
    public AdminAccount createAccount(Long actorId, String username, String displayName, String password) {
        authorizeWrite(actorId, AdminPermission.ACCOUNT_CREATE);
        validateUsername(username);
        validateName(displayName);
        validatePassword(password);
        AdminAccount account = newAccount(username, displayName.trim(), password);
        try {
            accounts.insert(account);
        } catch (DuplicateKeyException exception) {
            throw failure("管理员账号已存在");
        }
        return account;
    }

    @Override
    @Transactional
    public void updateAccount(Long actorId, Long id, String displayName) {
        authorizeWrite(actorId, AdminPermission.ACCOUNT_UPDATE);
        AdminAccount account = manageableAccount(actorId, id);
        validateName(displayName);
        account.setDisplayName(displayName.trim());
        accounts.updateById(account);
    }

    @Override
    @Transactional
    public void setAccountEnabled(Long actorId, Long id, boolean enabled) {
        authorizeWrite(actorId, AdminPermission.ACCOUNT_STATUS);
        AdminAccount account = manageableAccount(actorId, id);
        account.setEnabled(enabled);
        account.setSessionVersion(account.getSessionVersion() + 1);
        accounts.updateById(account);
    }

    @Override
    @Transactional
    public void replaceAccountRoles(Long actorId, Long id, List<Long> roleIds) {
        authorizeWrite(actorId, AdminPermission.ACCOUNT_ASSIGN_ROLE);
        manageableAccount(actorId, id);
        if (roleIds == null || roleIds.size() > 100 || roleIds.stream().anyMatch(v -> v == null || v < 1)) {
            throw failure("角色列表不合法");
        }
        Set<Long> distinct = new HashSet<>(roleIds);
        distinct.forEach(this::getRole);
        ensureSubset(actorId, permissionsForRoles(List.copyOf(distinct)));
        accountRoles.delete(new LambdaQueryWrapper<AdminAccountRole>().eq(AdminAccountRole::getAccountId, id));
        distinct.stream().sorted().forEach(roleId -> {
            AdminAccountRole relation = new AdminAccountRole();
            relation.setAccountId(id);
            relation.setRoleId(roleId);
            accountRoles.insert(relation);
        });
    }

    @Override
    @Transactional
    public void resetPassword(Long actorId, Long id, String password) {
        authorizeWrite(actorId, AdminPermission.ACCOUNT_RESET_PASSWORD);
        AdminAccount account = manageableAccount(actorId, id);
        validatePassword(password);
        account.setPasswordHash(encoder.encode(password));
        account.setPasswordChangeRequired(true);
        account.setSessionVersion(account.getSessionVersion() + 1);
        accounts.updateById(account);
    }

    @Override
    @Transactional
    public void changePassword(Long id, String oldPassword, String newPassword) {
        lockAdministration();
        AdminAccount account = getAccount(id);
        if (!Boolean.TRUE.equals(account.getEnabled()) || !encoder.matches(oldPassword, account.getPasswordHash())) {
            throw failure("原密码错误");
        }
        validatePassword(newPassword);
        if (encoder.matches(newPassword, account.getPasswordHash())) {
            throw failure("新密码不能与原密码相同");
        }
        account.setPasswordHash(encoder.encode(newPassword));
        account.setPasswordChangeRequired(false);
        account.setSessionVersion(account.getSessionVersion() + 1);
        accounts.updateById(account);
    }

    @Override
    public PageResult<AdminRole> listRoles(PageParam page, String keyword, Boolean enabled) {
        var query = new LambdaQueryWrapper<AdminRole>().eq(enabled != null, AdminRole::getEnabled, enabled);
        if (keyword != null && !keyword.isBlank()) {
            query.and(q -> q.like(AdminRole::getCode, keyword.trim()).or().like(AdminRole::getName, keyword.trim()));
        }
        return PageResult.from(roleMapper.selectPage(page, query.orderByAsc(AdminRole::getId)));
    }

    @Override
    public AdminRole getRole(Long id) {
        return roleMapper.selectByIdOpt(id).orElseThrow(() ->
                new BusinessException(ErrorCodeEnum.NOT_FOUND, "角色不存在"));
    }

    @Override
    @Transactional
    public AdminRole createRole(Long actorId, String code, String name) {
        authorizeWrite(actorId, AdminPermission.ROLE_CREATE);
        if (code == null || !code.matches("[a-z][a-z0-9_]{2,49}")) {
            throw failure("角色编码须为 3 到 50 位小写字母、数字或下划线，以字母开头");
        }
        validateName(name);
        AdminRole role = new AdminRole();
        role.setCode(code);
        role.setName(name.trim());
        role.setEnabled(true);
        try {
            roleMapper.insert(role);
        } catch (DuplicateKeyException exception) {
            throw failure("角色编码已存在");
        }
        return role;
    }

    @Override
    @Transactional
    public void updateRole(Long actorId, Long id, String name, boolean enabled) {
        authorizeWrite(actorId, AdminPermission.ROLE_UPDATE);
        AdminRole role = manageableRole(actorId, id);
        validateName(name);
        role.setName(name.trim());
        role.setEnabled(enabled);
        roleMapper.updateById(role);
    }

    @Override
    @Transactional
    public void deleteRole(Long actorId, Long id) {
        authorizeWrite(actorId, AdminPermission.ROLE_DELETE);
        manageableRole(actorId, id);
        if (accountRoles.selectCount(AdminAccountRole::getRoleId, id) > 0) {
            throw failure("角色已绑定管理员，请先解除绑定");
        }
        rolePermissions.delete(new LambdaQueryWrapper<AdminRolePermission>().eq(AdminRolePermission::getRoleId, id));
        roleMapper.deleteById(id);
    }

    @Override
    public List<String> rolePermissions(Long roleId) {
        getRole(roleId);
        return rolePermissions.selectList(AdminRolePermission::getRoleId, roleId).stream()
                .map(AdminRolePermission::getPermissionCode).sorted().toList();
    }

    @Override
    @Transactional
    public void replaceRolePermissions(Long actorId, Long id, List<String> permissions) {
        authorizeWrite(actorId, AdminPermission.ROLE_ASSIGN_PERMISSION);
        manageableRole(actorId, id);
        if (permissions == null || permissions.size() > AdminPermission.codes().size()
                || !AdminPermission.codes().containsAll(permissions)) {
            throw failure("包含未知权限点");
        }
        Set<String> distinct = new HashSet<>(permissions);
        ensureSubset(actorId, distinct);
        rolePermissions.delete(new LambdaQueryWrapper<AdminRolePermission>().eq(AdminRolePermission::getRoleId, id));
        distinct.stream().sorted().forEach(code -> {
            AdminRolePermission relation = new AdminRolePermission();
            relation.setRoleId(id);
            relation.setPermissionCode(code);
            rolePermissions.insert(relation);
        });
    }

    /** 统一锁定不可删除的初始账号，串行化授权写操作，避免检查后角色被并发修改。 */
    private void lockAdministration() {
        AdminAccount root = accounts.selectOne(new LambdaQueryWrapper<AdminAccount>()
                .eq(AdminAccount::getId, 1L).last("FOR UPDATE"));
        if (root == null || !Boolean.TRUE.equals(root.getSuperAdmin())) {
            throw failure("请先初始化超级管理员");
        }
    }

    private void authorizeWrite(Long actorId, AdminPermission permission) {
        lockAdministration();
        AdminAccount actor = getAccount(actorId);
        if (Boolean.TRUE.equals(actor.getPasswordChangeRequired()) || !permissions(actorId).contains(permission.code())) {
            throw failure("没有操作权限或需要先修改密码");
        }
    }

    private AdminAccount manageableAccount(Long actorId, Long id) {
        AdminAccount account = getAccount(id);
        if (actorId.equals(id) || Boolean.TRUE.equals(account.getSuperAdmin())) {
            throw failure("不能通过管理接口修改自身或超级管理员");
        }
        // 即使角色被禁用，也不能借重置密码/重新启用账号接管更高权限账号。
        ensureSubset(actorId, permissionsForRoles(accountRoleIds(id)));
        return account;
    }

    private AdminRole manageableRole(Long actorId, Long id) {
        AdminRole role = getRole(id);
        if (!Boolean.TRUE.equals(getAccount(actorId).getSuperAdmin()) && accountRoleIds(actorId).contains(id)) {
            throw failure("不能修改自身绑定的角色");
        }
        ensureSubset(actorId, new HashSet<>(rolePermissions(id)));
        return role;
    }

    private void ensureSubset(Long actorId, Set<String> requested) {
        if (!permissions(actorId).containsAll(requested)) {
            throw failure("不能管理或授予超出自身范围的权限");
        }
    }

    private AdminAccount newAccount(String username, String displayName, String password) {
        AdminAccount account = new AdminAccount();
        account.setUsername(username);
        account.setDisplayName(displayName);
        account.setPasswordHash(encoder.encode(password));
        account.setEnabled(true);
        account.setSuperAdmin(false);
        account.setPasswordChangeRequired(true);
        account.setSessionVersion(0L);
        return account;
    }

    private void validateUsername(String username) {
        if (username == null || !username.matches("[a-z][a-z0-9_]{2,49}")) {
            throw failure("账号须为 3 到 50 位小写字母、数字或下划线，以字母开头");
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 50) {
            throw failure("名称不能为空且不能超过 50 个字符");
        }
    }

    private void validatePassword(String password) {
        if (password == null || !password.matches("[\\x21-\\x7E]{8,72}")) {
            throw failure("密码须为 8 到 72 位非空格 ASCII 字符");
        }
    }

    private BusinessException failure(String message) {
        return new BusinessException(ErrorCodeEnum.FAIL, message);
    }
}
