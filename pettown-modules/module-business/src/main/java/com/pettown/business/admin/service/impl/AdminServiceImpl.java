package com.pettown.business.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.pettown.business.admin.entity.AdminAccount;
import com.pettown.business.admin.entity.AdminPermissionRecord;
import com.pettown.business.admin.mapper.AdminPermissionMapper;
import com.pettown.business.verification.service.VerificationCodeService;
import com.pettown.common.commonEnum.VerificationCodeTypeEnum;
import com.pettown.business.admin.entity.AdminRole;
import com.pettown.business.admin.entity.AdminRolePermission;
import com.pettown.business.admin.mapper.AdminAccountMapper;
import com.pettown.business.admin.mapper.AdminRoleMapper;
import com.pettown.business.admin.mapper.AdminRolePermissionMapper;
import com.pettown.business.admin.service.AdminPermission;
import com.pettown.business.admin.service.AdminService;
import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;
import com.pettown.common.mybatis.PageParam;
import com.pettown.common.mybatis.PageResult;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
    private final AdminRolePermissionMapper rolePermissions;
    private final AdminPermissionMapper permissionMapper;
    private final VerificationCodeService verification;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private final String dummyHash = encoder.encode("invalid-account-password");

    public AdminServiceImpl(AdminAccountMapper accounts, AdminRoleMapper roleMapper,
                            AdminRolePermissionMapper rolePermissions,
                            AdminPermissionMapper permissionMapper, VerificationCodeService verification) {
        this.accounts = accounts;
        this.roleMapper = roleMapper;
        this.rolePermissions = rolePermissions;
        this.permissionMapper = permissionMapper;
        this.verification = verification;
    }

    @Override
    @Transactional
    public void initializeSuperAdmin(String username, String password) {
        initializeSuperAdmin(username, password, null);
    }

    @Override
    @Transactional
    public void initializeSuperAdmin(String username, String password, String phone) {
        if (accounts.selectById(1L) != null) {
            return;
        }
        validateUsername(username);
        validatePassword(password);
        AdminAccount root = newAccount(username, "超级管理员", password);
        root.setId(1L);
        root.setSuperAdmin(true);
        validatePhone(phone);
        root.setPhone(phone);
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
        return findAccount(id).orElseThrow(() ->
                new BusinessException(ErrorCodeEnum.NOT_FOUND, "管理员不存在"));
    }

    @Override
    public Optional<AdminAccount> findAccount(Long id) {
        return accounts.selectByIdOpt(id);
    }

    @Override
    public Set<String> permissions(Long accountId) {
        AdminAccount account = getAccount(accountId);
        if (!Boolean.TRUE.equals(account.getEnabled())) {
            return Set.of();
        }
        if (Boolean.TRUE.equals(account.getSuperAdmin())) {
            return permissionCodes();
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
        Long roleId = getAccount(accountId).getRoleId();
        return roleId == null ? List.of() : List.of(roleId);
    }

    private Set<String> permissionsForRoles(List<Long> roleIds) {
        return rolePermissions.selectList(AdminRolePermission::getRoleId, roleIds).stream()
                .map(AdminRolePermission::getPermissionCode).filter(permissionCodes()::contains)
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
        return createAccount(actorId, username, displayName, password, null);
    }

    @Override
    @Transactional
    public AdminAccount createAccount(Long actorId, String username, String displayName, String password, String phone) {
        authorizeWrite(actorId, AdminPermission.ACCOUNT_CREATE);
        validateUsername(username);
        validateName(displayName);
        validatePassword(password);
        validatePhone(phone);
        AdminAccount account = newAccount(username, displayName.trim(), password);
        account.setPhone(phone);
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
        updateAccount(actorId, id, displayName, null);
    }

    @Override
    @Transactional
    public void updateAccount(Long actorId, Long id, String displayName, String phone) {
        authorizeWrite(actorId, AdminPermission.ACCOUNT_UPDATE);
        AdminAccount account = manageableAccount(actorId, id);
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
        authorizeWrite(actorId, AdminPermission.ACCOUNT_STATUS);
        AdminAccount account = manageableAccount(actorId, id);
        account.setEnabled(enabled);
        account.setSessionVersion(account.getSessionVersion() + 1);
        accounts.updateById(account);
    }

    @Override
    @Transactional
    public void replaceAccountRoles(Long actorId, Long id, List<Long> roleIds) {
        if (roleIds == null || roleIds.size() > 1 || roleIds.stream().anyMatch(v -> v == null || v < 1)) {
            throw failure("一个账号最多关联一个角色");
        }
        assignAccountRole(actorId, id, roleIds.isEmpty() ? null : roleIds.get(0));
    }

    @Override
    @Transactional
    public void assignAccountRole(Long actorId, Long id, Long roleId) {
        authorizeWrite(actorId, AdminPermission.ACCOUNT_ASSIGN_ROLE);
        manageableAccount(actorId, id);
        if (roleId != null) {
            if (roleId < 1) {
                throw failure("角色 ID 不合法");
            }
            getRole(roleId);
            ensureSubset(actorId, permissionsForRoles(List.of(roleId)));
        }
        // 使用显式 SET 保证 null 可真正清除绑定，不受实体的非 null 更新策略影响。
        accounts.update(new AdminAccount(), new LambdaUpdateWrapper<AdminAccount>()
                .eq(AdminAccount::getId, id).set(AdminAccount::getRoleId, roleId));
    }

    @Override
    @Transactional
    public void deleteAccount(Long actorId, Long id) {
        authorizeWrite(actorId, AdminPermission.ACCOUNT_DELETE);
        manageableAccount(actorId, id);
        // 账号只保存 role_id；删除账号不会删除角色或角色权限。
        accounts.deleteById(id);
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
        role.setDeleted(0);
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
        clearRoleBindings(id);
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
        if (permissions == null || permissions.size() > 1000
                || !permissionCodes().containsAll(permissions)) {
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

    private Set<String> permissionCodes() {
        Set<String> codes = new HashSet<>(AdminPermission.codes());
        permissionMapper.selectList().forEach(permission -> codes.add(permission.getCode()));
        return codes;
    }

    @Override
    public void generateLoginCode(String username) {
        AdminAccount account = accounts.selectOne(AdminAccount::getUsername, username);
        if (account == null || !Boolean.TRUE.equals(account.getEnabled())
                || account.getPhone() == null || !account.getPhone().matches("1[3-9][0-9]{9}")) {
            throw failure("管理员不存在、已禁用或未配置有效手机号");
        }
        // 只生成缓存；短信投递由后续发送服务接入，禁止在 HTTP 响应或日志中输出验证码。
        verification.generate(account.getPhone(), VerificationCodeTypeEnum.ADMIN_LOGIN);
    }

    @Override
    public AdminAccount authenticateWithCode(String username, String password, String smsCode) {
        AdminAccount account = authenticate(username, password);
        if (account.getPhone() == null || account.getPhone().isBlank()) {
            throw failure("管理员未配置手机号");
        }
        verification.verifyAndConsume(account.getPhone(), VerificationCodeTypeEnum.ADMIN_LOGIN, smsCode);
        return account;
    }

    @Override
    public void recordLogin(Long id) {
        AdminAccount update = new AdminAccount();
        update.setId(id);
        update.setLastLoginTime(LocalDateTime.now());
        accounts.updateById(update);
    }

    @Override
    @Transactional
    public AdminAccount createAdmin(Long actorId, String username, String password, String phone,
                                    String realName, Long roleId) {
        authorizeWrite(actorId, AdminPermission.SUB_ADMIN);
        validateUsername(username);
        validatePassword(password);
        validatePhone(phone);
        String name = realName == null ? username : realName;
        validateName(name);
        if (roleId != null) {
            getRole(roleId);
            ensureSubset(actorId, new HashSet<>(rolePermissions(roleId)));
        }
        AdminAccount account = newAccount(username, name.trim(), password);
        account.setPhone(phone);
        account.setRoleId(roleId);
        // 参考接口创建的账号可直接使用；原有账号管理接口仍保留首次改密契约。
        account.setPasswordChangeRequired(false);
        try {
            accounts.insert(account);
        } catch (DuplicateKeyException exception) {
            throw failure("管理员账号已存在");
        }
        return account;
    }

    @Override
    @Transactional
    public AdminRole createNamedRole(Long actorId, String rname, String description) {
        authorizeWrite(actorId, AdminPermission.SUB_ADMIN);
        validateName(rname);
        validateDescription(description);
        ensureRoleNameAvailable(rname.trim(), null);
        AdminRole role = new AdminRole();
        role.setCode("role_" + UUID.randomUUID().toString().replace("-", ""));
        role.setName(rname.trim());
        role.setDescription(description);
        role.setEnabled(true);
        role.setDeleted(0);
        roleMapper.insert(role);
        return role;
    }

    @Override
    public PageResult<AdminRole> listNamedRoles(PageParam page) {
        return PageResult.from(roleMapper.selectPage(page, new LambdaQueryWrapper<AdminRole>()
                .orderByDesc(AdminRole::getCreatedTime).orderByDesc(AdminRole::getId)));
    }

    @Override
    @Transactional
    public void updateNamedRole(Long actorId, Long id, String rname, String description) {
        authorizeWrite(actorId, AdminPermission.SUB_ADMIN);
        AdminRole role = manageableRole(actorId, id);
        if (rname != null) {
            validateName(rname);
            ensureRoleNameAvailable(rname.trim(), id);
            role.setName(rname.trim());
        }
        if (description != null) {
            validateDescription(description);
            role.setDescription(description);
        }
        roleMapper.updateById(role);
    }

    @Override
    @Transactional
    public void deleteNamedRole(Long actorId, Long id) {
        authorizeWrite(actorId, AdminPermission.SUB_ADMIN);
        manageableRole(actorId, id);
        rolePermissions.delete(new LambdaQueryWrapper<AdminRolePermission>().eq(AdminRolePermission::getRoleId, id));
        clearRoleBindings(id);
        roleMapper.deleteById(id);
    }

    private void clearRoleBindings(Long roleId) {
        accounts.update(new AdminAccount(), new LambdaUpdateWrapper<AdminAccount>()
                .eq(AdminAccount::getRoleId, roleId).set(AdminAccount::getRoleId, null));
    }

    private void ensureRoleNameAvailable(String name, Long exceptId) {
        if (roleMapper.selectCount(new LambdaQueryWrapper<AdminRole>().eq(AdminRole::getName, name)
                .ne(exceptId != null, AdminRole::getId, exceptId)) > 0) {
            throw failure("角色名称已存在");
        }
    }

    private void validateDescription(String description) {
        if (description != null && description.length() > 500) {
            throw failure("角色说明不能超过 500 个字符");
        }
    }

    private void validatePhone(String phone) {
        if (phone != null && !phone.matches("1[3-9][0-9]{9}")) {
            throw failure("手机号格式不正确");
        }
    }

    @Override
    public PageResult<AdminPermissionRecord> listPermissions(PageParam page) {
        return PageResult.from(permissionMapper.selectPage(page,
                new LambdaQueryWrapper<AdminPermissionRecord>().orderByDesc(AdminPermissionRecord::getId)));
    }

    @Override
    public AdminPermissionRecord getPermission(Long id) {
        return permissionMapper.selectByIdOpt(id).orElseThrow(() ->
                new BusinessException(ErrorCodeEnum.NOT_FOUND, "权限不存在"));
    }

    @Override
    @Transactional
    public void createPermission(Long actorId, String name, String code) {
        authorizeWrite(actorId, AdminPermission.SUB_ADMIN);
        insertPermission(name, code);
    }

    private void insertPermission(String name, String code) {
        validatePermission(name, code);
        AdminPermissionRecord permission = new AdminPermissionRecord();
        permission.setName(name.trim());
        permission.setCode(code);
        try {
            permissionMapper.insert(permission);
        } catch (DuplicateKeyException exception) {
            throw failure("权限编码已存在");
        }
    }

    private void validatePermission(String name, String code) {
        validateName(name);
        if (code == null || !code.matches("[a-zA-Z][a-zA-Z0-9_:/.-]{0,79}")) {
            throw failure("权限编码须为 1 到 80 位字母、数字或 _:/.-，以字母开头，不支持通配符");
        }
    }

    @Override
    @Transactional
    public void updatePermission(Long actorId, Long id, String name, String code) {
        authorizeWrite(actorId, AdminPermission.SUB_ADMIN);
        AdminPermissionRecord permission = getPermission(id);
        String nextName = name == null ? permission.getName() : name;
        String nextCode = code == null ? permission.getCode() : code;
        validatePermission(nextName, nextCode);
        if (AdminPermission.codes().contains(permission.getCode()) && !nextCode.equals(permission.getCode())) {
            throw failure("内置权限编码不能修改");
        }
        String previousCode = permission.getCode();
        permission.setName(nextName.trim());
        permission.setCode(nextCode);
        try {
            permissionMapper.updateById(permission);
        } catch (DuplicateKeyException exception) {
            throw failure("权限编码已存在");
        }
        // 关系存储编码，重命名时同一事务迁移，避免遗留旧编码或意外恢复授权。
        if (!previousCode.equals(nextCode)) {
            AdminRolePermission update = new AdminRolePermission();
            update.setPermissionCode(nextCode);
            rolePermissions.update(update, new LambdaQueryWrapper<AdminRolePermission>()
                    .eq(AdminRolePermission::getPermissionCode, previousCode));
        }
    }

    @Override
    @Transactional
    public void deletePermission(Long actorId, Long id) {
        authorizeWrite(actorId, AdminPermission.SUB_ADMIN);
        AdminPermissionRecord permission = getPermission(id);
        if (AdminPermission.codes().contains(permission.getCode())) {
            throw failure("内置权限不能删除");
        }
        rolePermissions.delete(new LambdaQueryWrapper<AdminRolePermission>()
                .eq(AdminRolePermission::getPermissionCode, permission.getCode()));
        permissionMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void syncPermissions(Long actorId, List<PermissionDefinition> permissions) {
        authorizeWrite(actorId, AdminPermission.SUB_ADMIN);
        if (permissions == null || permissions.isEmpty() || permissions.size() > 1000) {
            throw failure("权限列表须包含 1 到 1000 项");
        }
        Set<String> existingCodes = permissionMapper.selectList().stream()
                .map(AdminPermissionRecord::getCode).collect(Collectors.toSet());
        for (PermissionDefinition permission : permissions) {
            if (permission == null) {
                throw failure("权限项不能为空");
            }
            validatePermission(permission.name(), permission.code());
            if (existingCodes.add(permission.code())) {
                insertPermission(permission.name(), permission.code());
            }
        }
    }

    @Override
    public List<AdminPermissionRecord> rolePermissionRecords(Long roleId) {
        return permissionMapper.selectList(AdminPermissionRecord::getCode, rolePermissions(roleId));
    }

    @Override
    @Transactional
    public void replaceRolePermissionIds(Long actorId, Long roleId, List<Long> permissionIds) {
        authorizeWrite(actorId, AdminPermission.SUB_ADMIN);
        manageableRole(actorId, roleId);
        if (permissionIds == null || permissionIds.size() > 1000
                || permissionIds.stream().anyMatch(id -> id == null || id < 1)) {
            throw failure("权限 ID 列表不合法");
        }
        List<AdminPermissionRecord> records = permissionMapper.selectList(AdminPermissionRecord::getId,
                new HashSet<>(permissionIds));
        if (records.size() != new HashSet<>(permissionIds).size()) {
            throw failure("部分权限不存在");
        }
        Set<String> codes = records.stream().map(AdminPermissionRecord::getCode).collect(Collectors.toSet());
        ensureSubset(actorId, codes);
        rolePermissions.delete(new LambdaQueryWrapper<AdminRolePermission>().eq(AdminRolePermission::getRoleId, roleId));
        codes.stream().sorted().forEach(code -> {
            AdminRolePermission relation = new AdminRolePermission();
            relation.setRoleId(roleId);
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
