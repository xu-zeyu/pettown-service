package com.pettown.business.admin.service.impl;

import static com.pettown.business.admin.service.impl.AdminValidations.failure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pettown.business.admin.constant.AdminPermissionCodes;
import com.pettown.business.admin.entity.AdminAccount;
import com.pettown.business.admin.entity.AdminPermissionRecord;
import com.pettown.business.admin.entity.AdminRole;
import com.pettown.business.admin.entity.AdminRolePermission;
import com.pettown.business.admin.mapper.AdminAccountMapper;
import com.pettown.business.admin.mapper.AdminPermissionMapper;
import com.pettown.business.admin.mapper.AdminRoleMapper;
import com.pettown.business.admin.mapper.AdminRolePermissionMapper;
import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 管理端访问策略：有效权限解析与写操作越权校验。
 * 超级管理员（super_admin=1 且启用）持有全部权限；普通账号的有效权限来自已启用角色的绑定。
 * 所有授权写操作先在同一策略下串行校验，防止“检查后被并发提权”。
 */
@Component
public class AdminAccessPolicy {

    private final AdminAccountMapper accounts;
    private final AdminRoleMapper roles;
    private final AdminRolePermissionMapper rolePermissions;
    private final AdminPermissionMapper permissions;

    public AdminAccessPolicy(AdminAccountMapper accounts, AdminRoleMapper roles,
            AdminRolePermissionMapper rolePermissions, AdminPermissionMapper permissions) {
        this.accounts = accounts;
        this.roles = roles;
        this.rolePermissions = rolePermissions;
        this.permissions = permissions;
    }

    /** 账号当前有效权限编码；账号不存在或未启用时为空。超级管理员返回完整目录编码。 */
    public Set<String> permissions(Long accountId) {
        AdminAccount account = accounts.selectById(accountId);
        if (account == null || !Boolean.TRUE.equals(account.getEnabled())) {
            return Set.of();
        }
        if (Boolean.TRUE.equals(account.getSuperAdmin())) {
            return catalogCodes();
        }
        // 只有已启用角色提供有效权限；停用角色保留绑定但不生效。
        List<Long> enabledRoleIds = roles(accountId).stream().map(AdminRole::getId).toList();
        if (enabledRoleIds.isEmpty()) {
            return Set.of();
        }
        return permissions.selectByRoleIds(enabledRoleIds).stream()
                .map(AdminPermissionRecord::getCode)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 账号关联的已启用角色；单角色模型下最多一项。 */
    public List<AdminRole> roles(Long accountId) {
        return roles.selectList(AdminRole::getId, accountRoleIds(accountId)).stream()
                .filter(role -> Boolean.TRUE.equals(role.getEnabled()))
                .toList();
    }

    /** 账号关联的角色 ID；未关联时为空数组。 */
    public List<Long> accountRoleIds(Long accountId) {
        AdminAccount account = accounts.selectById(accountId);
        if (account == null || account.getRoleId() == null) {
            return List.of();
        }
        return List.of(account.getRoleId());
    }

    /** 角色绑定的权限编码，按编码排序；角色不存在时抛 404。 */
    public List<String> rolePermissionCodes(Long roleId) {
        requireRole(roleId);
        return rolePermissions.selectList(AdminRolePermission::getRoleId, roleId).stream()
                .map(AdminRolePermission::getPermissionCode)
                .sorted()
                .toList();
    }

    /** 角色绑定的权限目录记录，按编码排序；角色不存在时抛 404。 */
    public List<AdminPermissionRecord> rolePermissionRecords(Long roleId) {
        requireRole(roleId);
        return permissions.selectByRoleIds(List.of(roleId)).stream()
                .sorted((left, right) -> left.getCode().compareTo(right.getCode()))
                .toList();
    }

    /** 权限目录全集：内置编码恒定存在，叠加数据库目录（含自定义编码）。 */
    public Set<String> catalogCodes() {
        Set<String> codes = new HashSet<>(AdminPermissionCodes.builtinCodes());
        permissions.selectList().forEach(record -> codes.add(record.getCode()));
        return Set.copyOf(codes);
    }

    /** 查询账号，不存在时抛 404。 */
    public AdminAccount requireAccount(Long accountId) {
        AdminAccount account = accounts.selectById(accountId);
        if (account == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "管理员不存在");
        }
        return account;
    }

    /** 查询未删除角色，不存在时抛 404。 */
    public AdminRole requireRole(Long roleId) {
        AdminRole role = roles.selectById(roleId);
        if (role == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "角色不存在");
        }
        return role;
    }

    /** 查询权限目录项，不存在时抛 404。 */
    public AdminPermissionRecord requirePermission(Long permissionId) {
        AdminPermissionRecord record = permissions.selectById(permissionId);
        if (record == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "权限不存在");
        }
        return record;
    }

    /**
     * 写操作授权：操作者须启用、完成首次改密并持有指定权限编码。
     * 串行锁定超级管理员行，保证越权校验与后续写入之间不被并发修改。
     */
    public void authorizeWrite(Long actorId, String permissionCode) {
        lockAdministration();
        AdminAccount actor = requireAccount(actorId);
        if (Boolean.TRUE.equals(actor.getPasswordChangeRequired()) || !permissions(actorId).contains(permissionCode)) {
            throw failure("没有操作权限或需要先修改密码");
        }
    }

    /** 可管理的账号：不能修改自身、超级管理员或权限超出自身范围的账号。 */
    public AdminAccount requireManageableAccount(Long actorId, Long accountId) {
        AdminAccount account = requireAccount(accountId);
        if (actorId.equals(accountId) || Boolean.TRUE.equals(account.getSuperAdmin())) {
            throw failure("不能通过管理接口修改自身或超级管理员");
        }
        // 即使角色被禁用，也不能借重置密码/重新启用账号接管更高权限账号。
        ensureSubset(actorId, permissionCodesOfRoles(accountRoleIds(accountId)));
        return account;
    }

    /** 可管理的角色：非超管不能修改自身绑定的角色，且角色既有授权不得超出自身范围。 */
    public AdminRole requireManageableRole(Long actorId, Long roleId) {
        AdminRole role = requireRole(roleId);
        if (!Boolean.TRUE.equals(requireAccount(actorId).getSuperAdmin()) && accountRoleIds(actorId).contains(roleId)) {
            throw failure("不能修改自身绑定的角色");
        }
        ensureSubset(actorId, new HashSet<>(rolePermissionCodes(roleId)));
        return role;
    }

    /** 请求的权限编码必须是操作者有效权限的子集，防止委派越权授权或提权；超级管理员不受限。 */
    public void ensureSubset(Long actorId, Collection<String> requested) {
        if (Boolean.TRUE.equals(requireAccount(actorId).getSuperAdmin())) {
            return;
        }
        if (!permissions(actorId).containsAll(requested)) {
            throw failure("不能管理或授予超出自身范围的权限");
        }
    }

    /** 锁定超级管理员行串行化授权写操作；尚未初始化超管时拒绝写操作。 */
    public void lockAdministration() {
        AdminAccount root = accounts.selectOne(new LambdaQueryWrapper<AdminAccount>()
                .eq(AdminAccount::getSuperAdmin, true)
                .orderByAsc(AdminAccount::getId)
                .last("LIMIT 1 FOR UPDATE"));
        if (root == null) {
            throw failure("请先初始化超级管理员");
        }
    }

    /** 角色提供的权限编码（不要求角色存在，含已停用角色），用于越权子集校验。 */
    private Set<String> permissionCodesOfRoles(Collection<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        return permissions.selectByRoleIds(roleIds).stream()
                .map(AdminPermissionRecord::getCode)
                .collect(Collectors.toSet());
    }

    /** 账号有效权限解析的可选查询，供会话拦截等场景使用。 */
    public Optional<AdminAccount> findAccount(Long accountId) {
        return accounts.selectByIdOpt(accountId);
    }
}
