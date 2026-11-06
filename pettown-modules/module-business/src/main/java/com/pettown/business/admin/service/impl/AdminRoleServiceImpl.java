package com.pettown.business.admin.service.impl;

import static com.pettown.business.admin.service.impl.AdminValidations.failure;
import static com.pettown.business.admin.service.impl.AdminValidations.validateDescription;
import static com.pettown.business.admin.service.impl.AdminValidations.validateName;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.pettown.business.admin.constant.AdminPermissionCodes;
import com.pettown.business.admin.entity.AdminAccount;
import com.pettown.business.admin.entity.AdminPermissionRecord;
import com.pettown.business.admin.entity.AdminRole;
import com.pettown.business.admin.mapper.AdminAccountMapper;
import com.pettown.business.admin.mapper.AdminPermissionMapper;
import com.pettown.business.admin.mapper.AdminRoleMapper;
import com.pettown.business.admin.mapper.AdminRolePermissionMapper;
import com.pettown.business.admin.service.AdminRoleService;
import com.pettown.common.mybatis.PageParam;
import com.pettown.common.mybatis.PageResult;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理员角色实现：逻辑删除、单角色绑定与权限整组替换。
 * 旧接口（按编码）与参考接口（按名称）共用同一套私有流程，差异只在授权编码与入参形态。
 */
@Service
public class AdminRoleServiceImpl implements AdminRoleService {

    private final AdminRoleMapper roles;
    private final AdminAccountMapper accounts;
    private final AdminPermissionMapper permissions;
    private final AdminRolePermissionMapper rolePermissions;
    private final AdminAccessPolicy policy;

    public AdminRoleServiceImpl(AdminRoleMapper roles, AdminAccountMapper accounts,
            AdminPermissionMapper permissions, AdminRolePermissionMapper rolePermissions, AdminAccessPolicy policy) {
        this.roles = roles;
        this.accounts = accounts;
        this.permissions = permissions;
        this.rolePermissions = rolePermissions;
        this.policy = policy;
    }

    @Override
    public PageResult<AdminRole> listRoles(PageParam page, String keyword, Boolean enabled) {
        var query = new LambdaQueryWrapper<AdminRole>()
                .eq(enabled != null, AdminRole::getEnabled, enabled);
        if (keyword != null && !keyword.isBlank()) {
            query.and(q -> q.like(AdminRole::getCode, keyword.trim()).or().like(AdminRole::getName, keyword.trim()));
        }
        return PageResult.from(roles.selectPage(page, query.orderByAsc(AdminRole::getId)));
    }

    @Override
    public PageResult<AdminRole> listNamedRoles(PageParam page) {
        return PageResult.from(roles.selectPage(page, new LambdaQueryWrapper<AdminRole>()
                .orderByDesc(AdminRole::getCreatedTime).orderByDesc(AdminRole::getId)));
    }

    @Override
    public AdminRole getRole(Long id) {
        return policy.requireRole(id);
    }

    @Override
    @Transactional
    public AdminRole createNamedRole(Long actorId, String name, String description) {
        policy.authorizeWrite(actorId, AdminPermissionCodes.SUB_ADMIN);
        validateName(name);
        validateDescription(description);
        ensureRoleNameAvailable(name.trim(), null);
        return insertRole("role_" + UUID.randomUUID().toString().replace("-", ""), name.trim(), description);
    }

    @Override
    @Transactional
    public AdminRole createV1Role(Long actorId, String code, String name, String description) {
        policy.authorizeWrite(actorId, AdminPermissionCodes.ROLE_CREATE);
        if (code == null || !code.matches("[a-z][a-z0-9_]{2,49}")) {
            throw failure("角色编码须为 3 到 50 位小写字母、数字或下划线，以字母开头");
        }
        validateName(name);
        validateDescription(description);
        return insertRole(code, name.trim(), description);
    }

    @Override
    @Transactional
    public void updateRole(Long actorId, Long id, String name, boolean enabled) {
        AdminRole role = requireManageable(actorId, AdminPermissionCodes.ROLE_UPDATE, id);
        validateName(name);
        role.setName(name.trim());
        role.setEnabled(enabled);
        roles.updateById(role);
    }

    @Override
    @Transactional
    public void updateV1Role(Long actorId, Long id, String name, String description, boolean enabled) {
        requireManageable(actorId, AdminPermissionCodes.ROLE_UPDATE, id);
        validateName(name);
        validateDescription(description);
        roles.update(new AdminRole(), new LambdaUpdateWrapper<AdminRole>()
                .eq(AdminRole::getId, id)
                .set(AdminRole::getName, name.trim())
                .set(AdminRole::getDescription, description)
                .set(AdminRole::getEnabled, enabled));
    }

    @Override
    @Transactional
    public void updateNamedRole(Long actorId, Long id, String rname, String description) {
        AdminRole role = requireManageable(actorId, AdminPermissionCodes.SUB_ADMIN, id);
        if (rname != null) {
            validateName(rname);
            ensureRoleNameAvailable(rname.trim(), id);
            role.setName(rname.trim());
        }
        if (description != null) {
            validateDescription(description);
            role.setDescription(description);
        }
        roles.updateById(role);
    }

    @Override
    @Transactional
    public void deleteRole(Long actorId, Long id) {
        deleteRoleInternal(actorId, AdminPermissionCodes.ROLE_DELETE, id);
    }

    @Override
    @Transactional
    public void deleteV1Role(Long actorId, Long id) {
        requireManageable(actorId, AdminPermissionCodes.ROLE_DELETE, id);
        long references = accounts.selectCount(AdminAccount::getRoleId, id);
        if (references > 0) {
            throw failure("角色已被 " + references + " 个账号引用，不能删除");
        }
        clearRole(id);
    }

    @Override
    @Transactional
    public void deleteNamedRole(Long actorId, Long id) {
        deleteRoleInternal(actorId, AdminPermissionCodes.SUB_ADMIN, id);
    }

    @Override
    public List<String> rolePermissions(Long roleId) {
        return policy.rolePermissionCodes(roleId);
    }

    @Override
    public List<AdminPermissionRecord> rolePermissionRecords(Long roleId) {
        return policy.rolePermissionRecords(roleId);
    }

    @Override
    @Transactional
    public void replaceRolePermissions(Long actorId, Long roleId, List<String> permissionCodes) {
        policy.authorizeWrite(actorId, AdminPermissionCodes.ROLE_ASSIGN_PERMISSION);
        policy.requireManageableRole(actorId, roleId);
        if (permissionCodes == null || permissionCodes.size() > 1000
                || !policy.catalogCodes().containsAll(permissionCodes)) {
            throw failure("包含未知权限点");
        }
        replaceGrantedPermissions(actorId, roleId, new LinkedHashSet<>(permissionCodes));
    }

    @Override
    @Transactional
    public void replaceRolePermissionIds(Long actorId, Long roleId, List<Long> permissionIds) {
        policy.authorizeWrite(actorId, AdminPermissionCodes.SUB_ADMIN);
        policy.requireManageableRole(actorId, roleId);
        if (permissionIds == null || permissionIds.size() > 1000
                || permissionIds.stream().anyMatch(id -> id == null || id < 1)) {
            throw failure("权限 ID 列表不合法");
        }
        Set<Long> distinctIds = new LinkedHashSet<>(permissionIds);
        List<AdminPermissionRecord> records = permissions.selectList(AdminPermissionRecord::getId, distinctIds);
        if (records.size() != distinctIds.size()) {
            throw failure("部分权限不存在");
        }
        replaceGrantedPermissions(actorId, roleId, records.stream()
                .map(AdminPermissionRecord::getCode).collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    /** 授权并定位可管理角色。 */
    private AdminRole requireManageable(Long actorId, String permissionCode, Long id) {
        policy.authorizeWrite(actorId, permissionCode);
        return policy.requireManageableRole(actorId, id);
    }

    private void deleteRoleInternal(Long actorId, String permissionCode, Long id) {
        requireManageable(actorId, permissionCode, id);
        clearRole(id);
    }

    private AdminRole insertRole(String code, String name, String description) {
        AdminRole role = new AdminRole();
        role.setCode(code);
        role.setName(name);
        role.setDescription(description);
        role.setEnabled(true);
        role.setDeleted(0);
        try {
            roles.insert(role);
        } catch (DuplicateKeyException exception) {
            throw failure("角色编码已存在");
        }
        return role;
    }

    /** 越权子集校验后整组替换角色授权。 */
    private void replaceGrantedPermissions(Long actorId, Long roleId, Set<String> codes) {
        policy.ensureSubset(actorId, codes);
        replaceBindings(roleId, codes);
    }

    /** 整组替换角色授权：先清空再批量写入，空集合等价于清空。 */
    private void replaceBindings(Long roleId, Set<String> codes) {
        rolePermissions.deleteRolePermissions(roleId);
        if (!codes.isEmpty()) {
            rolePermissions.batchInsertRolePermissions(roleId, codes.stream().sorted().toList());
        }
    }

    /** 删除角色：清空关联账号的 role_id、清理角色权限并逻辑删除角色；账号与个人资料保留。 */
    private void clearRole(Long roleId) {
        accounts.update(new AdminAccount(), new LambdaUpdateWrapper<AdminAccount>()
                .eq(AdminAccount::getRoleId, roleId).set(AdminAccount::getRoleId, null));
        rolePermissions.deleteRolePermissions(roleId);
        roles.deleteById(roleId);
    }

    private void ensureRoleNameAvailable(String name, Long exceptId) {
        if (roles.selectCount(new LambdaQueryWrapper<AdminRole>().eq(AdminRole::getName, name)
                .ne(exceptId != null, AdminRole::getId, exceptId)) > 0) {
            throw failure("角色名称已存在");
        }
    }
}
