package com.pettown.business.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.pettown.business.admin.constant.AdminPermissionCodes;
import com.pettown.business.admin.service.AdminUserService;
import com.pettown.business.pet.entity.Pet;
import com.pettown.business.pet.mapper.PetMapper;
import com.pettown.business.user.entity.User;
import com.pettown.business.user.entity.UserStatus;
import com.pettown.business.user.mapper.UserMapper;
import com.pettown.common.commonEnum.UserSourceEnum;
import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;
import com.pettown.common.mybatis.PageParam;
import com.pettown.common.mybatis.PageResult;
import com.pettown.common.security.config.PetTownToken;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserServiceImpl implements AdminUserService {
    private final UserMapper users;
    private final PetMapper pets;
    private final AdminAccessPolicy policy;

    public AdminUserServiceImpl(UserMapper users, PetMapper pets, AdminAccessPolicy policy) {
        this.users = users;
        this.pets = pets;
        this.policy = policy;
    }

    @Override
    public PageResult<User> listUsers(PageParam page, String phone, UserSourceEnum source) {
        var query = new LambdaQueryWrapper<User>()
                .like(phone != null && !phone.isBlank(), User::getPhone, phone == null ? null : phone.trim())
                .eq(source != null, User::getSource, source)
                .orderByDesc(User::getCreatedAt).orderByDesc(User::getId);
        return PageResult.from(users.selectPage(page, query));
    }

    @Override
    public User getUser(String id) {
        return users.findById(id).orElseThrow(() -> new BusinessException(ErrorCodeEnum.NOT_FOUND, "用户不存在"));
    }

    @Override
    public List<Pet> listUserPets(String id) {
        getUser(id);
        // @TableLogic 自动排除软删；排序与移动端宠物列表一致（createdTime 倒序、id 倒序）
        return pets.selectList(new LambdaQueryWrapper<Pet>()
                .eq(Pet::getOwnerId, id)
                .orderByDesc(Pet::getCreatedTime)
                .orderByDesc(Pet::getId));
    }

    @Override
    @Transactional
    public void setUserStatus(Long actorId, String id, UserStatus status) {
        policy.authorizeWrite(actorId, AdminPermissionCodes.USER_STATUS);
        User user = getUser(id);
        if (user.getStatus() == status) {
            return;
        }
        users.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, id).set(User::getStatus, status));
        if (status == UserStatus.BANNED) {
            PetTownToken.USER.logout(id);
        }
    }
}
