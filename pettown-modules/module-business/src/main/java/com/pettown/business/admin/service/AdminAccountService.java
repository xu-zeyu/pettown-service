package com.pettown.business.admin.service;

import com.pettown.business.admin.entity.AdminAccount;
import com.pettown.common.mybatis.PageParam;
import com.pettown.common.mybatis.PageResult;
import java.util.List;
import java.util.Optional;

/** 管理员账号生命周期：初始化、资料、状态、角色绑定、密码与登录记录。 */
public interface AdminAccountService {

    /** 无超级管理员时创建启用的超管（自增 ID、须首次改密）；已有超管则幂等跳过。 */
    void initializeSuperAdmin(String username, String password, String phone);

    AdminAccount getAccount(Long id);

    Optional<AdminAccount> findAccount(Long id);

    PageResult<AdminAccount> listAccounts(PageParam page, String keyword, Boolean enabled);

    /** v1 管理端独立筛选：账号和显示名分别模糊匹配，状态和当前关联角色 ID 精确匹配。 */
    PageResult<AdminAccount> searchAccounts(PageParam page, String username, String displayName,
            Boolean enabled, Long roleId);

    /** 旧账号管理接口创建的账号须首次改密。 */
    AdminAccount createAccount(Long actorId, String username, String displayName, String password, String phone);

    /** v1 管理端创建：可指定不越权的角色，初始密码须强校验，首次登录强制改密。 */
    AdminAccount createV1Account(Long actorId, String username, String displayName, String password,
            String phone, Long roleId);

    /** 参考接口创建的账号可直接使用，无需首次改密。 */
    AdminAccount createAdmin(Long actorId, String username, String password, String phone, String realName, Long roleId);

    /** 修改显示名及可选手机号；手机号变更使旧会话失效。 */
    void updateAccount(Long actorId, Long id, String displayName, String phone);

    /** 启停账号；任一方向的状态变更都使全部旧会话失效。 */
    void setAccountEnabled(Long actorId, Long id, boolean enabled);

    /** 兼容旧接口的角色数组；最多一项，空数组解除关联。 */
    void replaceAccountRoles(Long actorId, Long id, List<Long> roleIds);

    /** 设置账号唯一角色；roleId 为 null 时解除关联。 */
    void assignAccountRole(Long actorId, Long id, Long roleId);

    /** 删除账号并注销其全部会话；保留角色、角色权限及使用该角色的其他账号。 */
    void deleteAccount(Long actorId, Long id);

    /** 重置密码；使全部旧会话失效，下次登录必须修改密码。 */
    void resetPassword(Long actorId, Long id, String password);

    /** 本人改密；校验原密码后更新，使全部旧会话失效。 */
    void changePassword(Long id, String oldPassword, String newPassword);

    /** 记录最近登录时间。 */
    void recordLogin(Long id);
}
