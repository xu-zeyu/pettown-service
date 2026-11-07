package com.pettown.business.admin.service;

import com.pettown.business.pet.entity.Pet;
import com.pettown.business.user.entity.User;
import com.pettown.business.user.entity.UserStatus;
import com.pettown.common.commonEnum.UserSourceEnum;
import com.pettown.common.mybatis.PageParam;
import com.pettown.common.mybatis.PageResult;
import java.util.List;

/** 管理端 C 端用户查询与状态操作。 */
public interface AdminUserService {
    PageResult<User> listUsers(PageParam page, String phone, UserSourceEnum source);

    User getUser(String id);

    /** 指定用户的宠物列表（不含健康记录），软删排除，与移动端列表同排序；用户不存在抛 NOT_FOUND。 */
    List<Pet> listUserPets(String id);

    void setUserStatus(Long actorId, String id, UserStatus status);
}
