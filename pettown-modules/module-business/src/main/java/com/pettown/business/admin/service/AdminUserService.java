package com.pettown.business.admin.service;

import com.pettown.business.user.entity.User;
import com.pettown.business.user.entity.UserStatus;
import com.pettown.common.commonEnum.UserSourceEnum;
import com.pettown.common.mybatis.PageParam;
import com.pettown.common.mybatis.PageResult;

/** 管理端 C 端用户查询与状态操作。 */
public interface AdminUserService {
    PageResult<User> listUsers(PageParam page, String phone, UserSourceEnum source);

    User getUser(String id);

    void setUserStatus(Long actorId, String id, UserStatus status);
}
