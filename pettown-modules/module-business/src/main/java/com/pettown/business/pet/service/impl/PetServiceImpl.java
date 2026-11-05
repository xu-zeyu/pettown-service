package com.pettown.business.pet.service.impl;

import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;
import com.pettown.business.pet.entity.Pet;
import com.pettown.business.pet.mapper.PetMapper;
import com.pettown.business.pet.service.PetService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PetServiceImpl implements PetService {
    private final PetMapper petMapper;

    public PetServiceImpl(PetMapper petMapper) {
        this.petMapper = petMapper;
    }

    @Override
    public Pet createPet(String ownerId, String name, String species) {
        Pet pet = new Pet(null, name.trim(), species.trim(), ownerId);
        petMapper.insert(pet);
        return pet;
    }

    @Override
    public Pet getPet(String ownerId, Long id) {
        // 归属过滤与不存在返回同一错误，防止跨用户探测宠物是否存在
        // 归属过滤与不存在返回同一错误，防止跨用户探测宠物是否存在
        return petMapper.selectOneOpt(new LambdaQueryWrapper<Pet>()
                        .eq(Pet::getId, id)
                        .eq(Pet::getOwnerId, ownerId))
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.NOT_FOUND, "宠物不存在"));
    }

    @Override
    public List<Pet> listPets(String ownerId) {
        return petMapper.selectList(new LambdaQueryWrapper<Pet>()
                .eq(Pet::getOwnerId, ownerId)
                .orderByAsc(Pet::getId));
    }
}
