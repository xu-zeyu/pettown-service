package com.pettown.miniapp.pet.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.pettown.business.pet.entity.Pet;
import com.pettown.business.pet.entity.PetHealthRecord;
import com.pettown.business.pet.mapper.PetHealthRecordMapper;
import com.pettown.business.pet.mapper.PetMapper;
import com.pettown.business.user.entity.User;
import com.pettown.business.user.mapper.UserMapper;
import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;
import com.pettown.common.mybatis.PageParam;
import com.pettown.common.mybatis.PageResult;
import com.pettown.miniapp.pet.dto.CreatePetProfileRequest;
import com.pettown.miniapp.pet.vo.PetProfileResponse;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MiniappPetProfileService {
    private static final long PET_LIMIT = 20;
    private final UserMapper users;
    private final PetMapper pets;
    private final PetHealthRecordMapper records;

    public MiniappPetProfileService(UserMapper users, PetMapper pets, PetHealthRecordMapper records) {
        this.users = users;
        this.pets = pets;
        this.records = records;
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

    public Pet get(String ownerId, Long id) {
        return pets.selectOneOpt(new LambdaQueryWrapper<Pet>()
                        .eq(Pet::getId, id)
                        .eq(Pet::getOwnerId, ownerId))
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.NOT_FOUND, "宠物不存在"));
    }

    @Transactional
    public Pet update(String ownerId, Long id, CreatePetProfileRequest request) {
        pets.update(null, new LambdaUpdateWrapper<Pet>()
                .eq(Pet::getId, id)
                .eq(Pet::getOwnerId, ownerId)
                .set(Pet::getName, request.name().trim())
                .set(Pet::getSpecies, request.species().name())
                .set(Pet::getBreed, request.breed())
                .set(Pet::getGender, request.gender())
                .set(Pet::getBirthDate, request.birthDate())
                .set(Pet::getWeightKg, request.weightKg())
                .set(Pet::getSterilized, request.sterilized())
                .set(Pet::getAvatarUrl, request.avatarUrl())
                .set(Pet::getUpdatedTime, LocalDateTime.now()));
        // 数据库驱动可能把未改变值的更新计为 0 行；按相同归属条件回读统一判定存在性。
        return get(ownerId, id);
    }

    @Transactional
    public void delete(String ownerId, Long id) {
        Pet pet = pets.selectOne(new LambdaQueryWrapper<Pet>()
                .eq(Pet::getId, id)
                .eq(Pet::getOwnerId, ownerId)
                .last("FOR UPDATE"));
        if (pet == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "宠物不存在");
        }
        records.delete(new LambdaQueryWrapper<PetHealthRecord>().eq(PetHealthRecord::getPetId, pet.getId()));
        if (pets.delete(new LambdaQueryWrapper<Pet>()
                .eq(Pet::getId, pet.getId())
                .eq(Pet::getOwnerId, ownerId)) != 1) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "宠物不存在");
        }
    }
}
