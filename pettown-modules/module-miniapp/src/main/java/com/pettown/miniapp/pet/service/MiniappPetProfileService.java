package com.pettown.miniapp.pet.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pettown.business.pet.entity.Pet;
import com.pettown.business.pet.mapper.PetMapper;
import com.pettown.business.user.entity.User;
import com.pettown.business.user.mapper.UserMapper;
import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;
import com.pettown.common.mybatis.PageParam;
import com.pettown.common.mybatis.PageResult;
import com.pettown.miniapp.pet.dto.CreatePetProfileRequest;
import com.pettown.miniapp.pet.vo.PetProfileResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MiniappPetProfileService {
    private static final long PET_LIMIT = 20;
    private final UserMapper users;
    private final PetMapper pets;

    public MiniappPetProfileService(UserMapper users, PetMapper pets) {
        this.users = users;
        this.pets = pets;
    }

    @Transactional
    public Pet create(String ownerId, CreatePetProfileRequest request) {
        // 同一用户的创建请求先锁同一行，串行完成数量检查和插入。
        User owner = users.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, ownerId).last("FOR UPDATE"));
        if (owner == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "用户不存在");
        }
        long count = pets.selectCount(new LambdaQueryWrapper<Pet>().eq(Pet::getOwnerId, ownerId));
        if (count >= PET_LIMIT) {
            throw new BusinessException(ErrorCodeEnum.PET_LIMIT_REACHED, "每个用户最多创建 20 只宠物");
        }
        Pet pet = new Pet(null, request.name().trim(), request.species().name(), ownerId);
        pet.setBreed(request.breed());
        pet.setGender(request.gender());
        pet.setBirthDate(request.birthDate());
        pet.setWeightKg(request.weightKg());
        pet.setSterilized(request.sterilized());
        pet.setAvatarUrl(request.avatarUrl());
        pets.insert(pet);
        return pet;
    }

    public PageResult<PetProfileResponse> list(String ownerId, int page, int size) {
        var result = pets.selectPage(new PageParam().setPage(page).setSize(size),
                new LambdaQueryWrapper<Pet>()
                        .eq(Pet::getOwnerId, ownerId)
                        .orderByDesc(Pet::getCreatedTime)
                        .orderByDesc(Pet::getId));
        return new PageResult<>(result.getRecords().stream().map(PetProfileResponse::from).toList(),
                result.getTotal());
    }
}
