package com.pettown.business.admin.service.impl;

import static com.pettown.business.admin.service.impl.AdminValidations.failure;
import static com.pettown.business.admin.service.impl.AdminValidations.validatePermission;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.pettown.business.admin.constant.AdminPermissionCodes;
import com.pettown.business.admin.entity.AdminPermissionRecord;
import com.pettown.business.admin.entity.AdminRolePermission;
import com.pettown.business.admin.mapper.AdminPermissionMapper;
import com.pettown.business.admin.mapper.AdminRolePermissionMapper;
import com.pettown.business.admin.service.AdminPermissionService;
import com.pettown.business.admin.service.AdminService;
import com.pettown.common.mybatis.PageParam;
import com.pettown.common.mybatis.PageResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 权限目录实现：内置编码只读，自定义编码改名同事务迁移绑定。 */
@Service
public class AdminPermissionServiceImpl implements AdminPermissionService {

    private final AdminPermissionMapper permissions;
    private final AdminRolePermissionMapper rolePermissions;
    private final AdminAccessPolicy policy;

    public AdminPermissionServiceImpl(AdminPermissionMapper permissions,
            AdminRolePermissionMapper rolePermissions, AdminAccessPolicy policy) {
        this.permissions = permissions;
        this.rolePermissions = rolePermissions;
        this.policy = policy;
    }

    @Override
    public PageResult<AdminPermissionRecord> listPermissions(PageParam page) {
        return PageResult.from(permissions.selectPage(page,
                new LambdaQueryWrapper<AdminPermissionRecord>().orderByDesc(AdminPermissionRecord::getId)));
    }

    @Override
    public AdminPermissionRecord getPermission(Long id) {
        return policy.requirePermission(id);
    }

    @Override
    @Transactional
    public void createPermission(Long actorId, String name, String code) {
        policy.authorizeWrite(actorId, AdminPermissionCodes.SUB_ADMIN);
        insertPermission(name, code);
    }

    @Override
    @Transactional
    public void updatePermission(Long actorId, Long id, String name, String code) {
        policy.authorizeWrite(actorId, AdminPermissionCodes.SUB_ADMIN);
        AdminPermissionRecord permission = policy.requirePermission(id);
        String nextName = name == null ? permission.getName() : name;
        String nextCode = code == null ? permission.getCode() : code;
        validatePermission(nextName, nextCode);
        if (AdminPermissionCodes.builtinCodes().contains(permission.getCode()) && !nextCode.equals(permission.getCode())) {
            throw failure("内置权限编码不能修改");
        }
        // 非超管只能修改自己持有的权限；改码时还须持有目标编码，防止借改码扩大授权。
        policy.ensureSubset(actorId, Set.of(permission.getCode()));
        if (!nextCode.equals(permission.getCode())) {
            policy.ensureSubset(actorId, Set.of(nextCode));
        }
        String previousCode = permission.getCode();
        permission.setName(nextName.trim());
        permission.setCode(nextCode);
        try {
            permissions.updateById(permission);
        } catch (DuplicateKeyException exception) {
            throw failure("权限编码已存在");
        }
        // 关系存储编码，重命名时同一事务迁移，避免遗留旧编码或意外恢复授权。
        if (!previousCode.equals(nextCode)) {
            rolePermissions.update(new AdminRolePermission(), new LambdaUpdateWrapper<AdminRolePermission>()
                    .eq(AdminRolePermission::getPermissionCode, previousCode)
                    .set(AdminRolePermission::getPermissionCode, nextCode));
        }
    }

    @Override
    @Transactional
    public void deletePermission(Long actorId, Long id) {
        policy.authorizeWrite(actorId, AdminPermissionCodes.SUB_ADMIN);
        AdminPermissionRecord permission = policy.requirePermission(id);
        if (AdminPermissionCodes.builtinCodes().contains(permission.getCode())) {
            throw failure("内置权限不能删除");
        }
        // 非超管只能删除自己持有的权限。
        policy.ensureSubset(actorId, Set.of(permission.getCode()));
        rolePermissions.delete(new LambdaQueryWrapper<AdminRolePermission>()
                .eq(AdminRolePermission::getPermissionCode, permission.getCode()));
        permissions.deleteById(id);
    }

    @Override
    @Transactional
    public void syncPermissions(Long actorId, List<AdminService.PermissionDefinition> definitions) {
        policy.authorizeWrite(actorId, AdminPermissionCodes.SUB_ADMIN);
        if (definitions == null || definitions.isEmpty() || definitions.size() > 1000) {
            throw failure("权限列表须包含 1 到 1000 项");
        }
        // 全量校验通过后才写入；同批重复编码只创建一次且保留首个名称。
        Map<String, String> additions = new LinkedHashMap<>();
        for (AdminService.PermissionDefinition definition : definitions) {
            if (definition == null) {
                throw failure("权限项不能为空");
            }
            validatePermission(definition.name(), definition.code());
            additions.putIfAbsent(definition.code(), definition.name());
        }
        Set<String> existingCodes = policy.catalogCodes();
        additions.forEach((code, name) -> {
            if (!existingCodes.contains(code)) {
                insertPermission(name, code);
            }
        });
    }

    private void insertPermission(String name, String code) {
        validatePermission(name, code);
        AdminPermissionRecord permission = new AdminPermissionRecord();
        permission.setName(name.trim());
        permission.setCode(code);
        try {
            permissions.insert(permission);
        } catch (DuplicateKeyException exception) {
            throw failure("权限编码已存在");
        }
    }
}
