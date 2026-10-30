package com.pettown.business.pet.service.impl;

import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;
import com.pettown.business.pet.entity.Pet;
import com.pettown.business.pet.mapper.PetMapper;
import com.pettown.business.pet.service.PetService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PetServiceImpl implements PetService {
    private final PetMapper petMapper;

    public PetServiceImpl(PetMapper petMapper) {
        this.petMapper = petMapper;
    }

    @Override
    public Pet createPet(String name, String species) {
        return petMapper.insert(name.trim(), species.trim());
    }

    @Override
    public Pet getPet(Long id) {
        return petMapper.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCodeEnum.NOT_FOUND, "宠物不存在"));
    }

    @Override
    public List<Pet> listPets() {
        return petMapper.findAll();
    }
}
