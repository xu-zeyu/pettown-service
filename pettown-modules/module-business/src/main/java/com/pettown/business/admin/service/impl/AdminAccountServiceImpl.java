package com.pettown.business.admin.service.impl;

import static com.pettown.business.admin.service.impl.AdminValidations.failure;
import static com.pettown.business.admin.service.impl.AdminValidations.validateName;
import static com.pettown.business.admin.service.impl.AdminValidations.validatePassword;
import static com.pettown.business.admin.service.impl.AdminValidations.validatePhone;
import static com.pettown.business.admin.service.impl.AdminValidations.validateUsername;
import static com.pettown.business.admin.service.impl.AdminValidations.validateV1Password;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.pettown.business.admin.constant.AdminPermissionCodes;
import com.pettown.business.admin.entity.AdminAccount;
import com.pettown.business.admin.mapper.AdminAccountMapper;
import com.pettown.business.admin.service.AdminAccountService;
import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;
import com.pettown.common.mybatis.PageParam;
import com.pettown.common.mybatis.PageResult;
import com.pettown.common.utils.PasswordService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理员账号生命周期实现。 */
@Service
public class AdminAccountServiceImpl implements AdminAccountService {

    private final AdminAccountMapper accounts;
    private final AdminAccessPolicy policy;
    private final PasswordService passwordService;

    public AdminAccountServiceImpl(AdminAccountMapper accounts, AdminAccessPolicy policy,
            PasswordService passwordService) {
        this.accounts = accounts;
        this.policy = policy;
        this.passwordService = passwordService;
    }

    @Override
    @Transactional
    public void initializeSuperAdmin(String username, String password, String phone) {
        if (accounts.selectCount(new LambdaQueryWrapper<AdminAccount>()
                .eq(AdminAccount::getSuperAdmin, true)) > 0) {
            return;
        }
        validateUsername(username);
        validatePassword(password);
        validatePhone(phone);
        AdminAccount root = newAccount(username, "超级管理员", password);
        root.setSuperAdmin(true);
        root.setPhone(phone);
        // 与部署文档一致：ID=1 空闲时使用 1，被普通账号占用时退回自增 ID，不覆盖已有账号。
        if (accounts.selectById(1L) == null) {
            root.setId(1L);
        }
        accounts.insert(root);
    }

    @Override
    public AdminAccount getAccount(Long id) {
        return policy.requireAccount(id);
    }

    @Override
    public Optional<AdminAccount> findAccount(Long id) {
        return policy.findAccount(id);
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
    public PageResult<AdminAccount> searchAccounts(PageParam page, String username, String displayName,
            Boolean enabled, Long roleId) {
        var query = new LambdaQueryWrapper<AdminAccount>()
                .eq(enabled != null, AdminAccount::getEnabled, enabled)
                .eq(roleId != null, AdminAccount::getRoleId, roleId);
        if (username != null && !username.isBlank()) {
            query.like(AdminAccount::getUsername, username.trim());
        }
        if (displayName != null && !displayName.isBlank()) {
            query.like(AdminAccount::getDisplayName, displayName.trim());
        }
        return PageResult.from(accounts.selectPage(page, query.orderByAsc(AdminAccount::getId)));
    }

    @Override
    @Transactional
    public AdminAccount createAccount(Long actorId, String username, String displayName, String password, String phone) {
        policy.authorizeWrite(actorId, AdminPermissionCodes.ACCOUNT_CREATE);
        validateUsername(username);
        validateName(displayName);
        validatePassword(password);
        validatePhone(phone);
        return insertNewAccount(username, displayName.trim(), password, phone, null, true);
    }

    @Override
    @Transactional
    public AdminAccount createV1Account(Long actorId, String username, String displayName, String password,
            String phone, Long roleId) {
        policy.authorizeWrite(actorId, AdminPermissionCodes.ACCOUNT_CREATE);
        if (username == null || !username.matches("[A-Za-z0-9]{4,50}")) {
            throw failure("用户名须为 4 到 50 位字母或数字");
        }
        validateName(displayName);
        if (password == null || !password.matches("[\\x21-\\x7E]{8,72}")
                || !password.matches(".*[A-Za-z].*")
                || !password.matches(".*[0-9].*")
                || !password.matches(".*[^A-Za-z0-9].*")) {
            throw failure("初始密码须为 8 到 72 位非空格 ASCII，且包含字母、数字、符号");
        }
        validatePhone(phone);
        if (roleId != null) {
            policy.requireRole(roleId);
            policy.ensureSubset(actorId, policy.rolePermissionCodes(roleId));
        }
        return insertNewAccount(username, displayName.trim(), password, phone, roleId, true,
                ErrorCodeEnum.ADMIN_USERNAME_EXISTS);
    }

    @Override
    @Transactional
    public AdminAccount createAdmin(Long actorId, String username, String password, String phone,
            String realName, Long roleId) {
        policy.authorizeWrite(actorId, AdminPermissionCodes.SUB_ADMIN);
        validateUsername(username);
        validatePassword(password);
        validatePhone(phone);
        String name = realName == null ? username : realName;
        validateName(name);
        if (roleId != null) {
            policy.requireRole(roleId);
            policy.ensureSubset(actorId, policy.rolePermissionCodes(roleId));
        }
        // 参考接口创建的账号可直接使用；原有账号管理接口仍保留首次改密契约。
        return insertNewAccount(username, name.trim(), password, phone, roleId, false);
    }

    @Override
    @Transactional
    public void updateAccount(Long actorId, Long id, String displayName, String phone) {
        policy.authorizeWrite(actorId, AdminPermissionCodes.ACCOUNT_UPDATE);
        AdminAccount account = policy.requireManageableAccount(actorId, id);
        validateName(displayName);
        account.setDisplayName(displayName.trim());
        validatePhone(phone);
        if (phone != null && !phone.equals(account.getPhone())) {
            account.setPhone(phone);
            account.setSessionVersion(account.getSessionVersion() + 1);
        }
        accounts.updateById(account);
    }

    @Override
    @Transactional
    public void setAccountEnabled(Long actorId, Long id, boolean enabled) {
        policy.authorizeWrite(actorId, AdminPermissionCodes.ACCOUNT_STATUS);
        AdminAccount account = policy.requireManageableAccount(actorId, id);
        account.setEnabled(enabled);
        account.setSessionVersion(account.getSessionVersion() + 1);
        accounts.updateById(account);
    }

    @Override
    @Transactional
    public void replaceAccountRoles(Long actorId, Long id, List<Long> roleIds) {
        if (roleIds == null || roleIds.size() > 1 || roleIds.stream().anyMatch(value -> value == null || value < 1)) {
            throw failure("一个账号最多关联一个角色");
        }
        assignAccountRole(actorId, id, roleIds.isEmpty() ? null : roleIds.get(0));
    }

    @Override
    @Transactional
    public void assignAccountRole(Long actorId, Long id, Long roleId) {
        policy.authorizeWrite(actorId, AdminPermissionCodes.ACCOUNT_ASSIGN_ROLE);
        policy.requireManageableAccount(actorId, id);
        if (roleId != null) {
            if (roleId < 1) {
                throw failure("角色 ID 不合法");
            }
            policy.requireRole(roleId);
            policy.ensureSubset(actorId, policy.rolePermissionCodes(roleId));
        }
        // 显式 SET 保证 null 可真正清除绑定，不受实体非 null 更新策略影响。
        accounts.update(new AdminAccount(), new LambdaUpdateWrapper<AdminAccount>()
                .eq(AdminAccount::getId, id).set(AdminAccount::getRoleId, roleId));
    }

    @Override
    @Transactional
    public void deleteAccount(Long actorId, Long id) {
        policy.authorizeWrite(actorId, AdminPermissionCodes.ACCOUNT_DELETE);
        policy.requireManageableAccount(actorId, id);
        // @TableLogic 软删账号；角色及角色权限保留，账号旧 Token 由控制器注销或会话拦截器拒绝。
        accounts.deleteById(id);
    }

    @Override
    @Transactional
    public void resetPassword(Long actorId, Long id, String password) {
        resetPasswordInternal(actorId, id, password, false);
    }

    @Override
    @Transactional
    public void resetV1Password(Long actorId, Long id, String password) {
        resetPasswordInternal(actorId, id, password, true);
    }

    private void resetPasswordInternal(Long actorId, Long id, String password, boolean v1) {
        policy.authorizeWrite(actorId, AdminPermissionCodes.ACCOUNT_RESET_PASSWORD);
        AdminAccount account = policy.requireManageableAccount(actorId, id);
        if (v1) {
            validateV1Password(password);
        } else {
            validatePassword(password);
        }
        account.setPasswordHash(passwordService.passwordEncrypt(password));
        account.setPasswordChangeRequired(true);
        account.setSessionVersion(account.getSessionVersion() + 1);
        accounts.updateById(account);
    }

    @Override
    @Transactional
    public void changePassword(Long id, String oldPassword, String newPassword) {
        changePasswordInternal(id, oldPassword, newPassword, false);
    }

    @Override
    @Transactional
    public void changeV1Password(Long id, String oldPassword, String newPassword) {
        changePasswordInternal(id, oldPassword, newPassword, true);
    }

    private void changePasswordInternal(Long id, String oldPassword, String newPassword, boolean v1) {
        policy.lockAdministration();
        AdminAccount account = policy.requireAccount(id);
        if (!Boolean.TRUE.equals(account.getEnabled())
                || !passwordService.passwordValid(oldPassword, account.getPasswordHash())) {
            throw failure("原密码错误");
        }
        if (v1) {
            validateV1Password(newPassword);
        } else {
            validatePassword(newPassword);
        }
        if (passwordService.passwordValid(newPassword, account.getPasswordHash())) {
            throw failure("新密码不能与原密码相同");
        }
        account.setPasswordHash(passwordService.passwordEncrypt(newPassword));
        account.setPasswordChangeRequired(false);
        account.setSessionVersion(account.getSessionVersion() + 1);
        accounts.updateById(account);
    }

    @Override
    public void recordLogin(Long id) {
        AdminAccount update = new AdminAccount();
        update.setId(id);
        update.setLastLoginTime(LocalDateTime.now());
        accounts.updateById(update);
    }

    /**
     * 统一落库新账号；passwordChangeRequired 区分旧账号管理接口（须首次改密）
     * 与参考接口（可直接使用），roleId 仅参考接口创建时携带。
     */
    private AdminAccount insertNewAccount(String username, String displayName, String password,
            String phone, Long roleId, boolean passwordChangeRequired) {
        return insertNewAccount(username, displayName, password, phone, roleId, passwordChangeRequired,
                ErrorCodeEnum.FAIL);
    }

    private AdminAccount insertNewAccount(String username, String displayName, String password,
            String phone, Long roleId, boolean passwordChangeRequired, ErrorCodeEnum duplicateCode) {
        AdminAccount account = newAccount(username, displayName, password);
        account.setPhone(phone);
        account.setRoleId(roleId);
        account.setPasswordChangeRequired(passwordChangeRequired);
        insertAccount(account, duplicateCode);
        return account;
    }

    private void insertAccount(AdminAccount account, ErrorCodeEnum duplicateCode) {
        try {
            accounts.insert(account);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(duplicateCode, "管理员账号已存在");
        }
    }

    private AdminAccount newAccount(String username, String displayName, String password) {
        AdminAccount account = new AdminAccount();
        account.setUsername(username);
        account.setDisplayName(displayName);
        account.setPasswordHash(passwordService.passwordEncrypt(password));
        account.setEnabled(true);
        account.setSuperAdmin(false);
        account.setPasswordChangeRequired(true);
        account.setSessionVersion(0L);
        return account;
    }
}
