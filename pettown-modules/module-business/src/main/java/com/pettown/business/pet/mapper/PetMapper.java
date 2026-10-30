package com.pettown.business.pet.mapper;

import com.pettown.business.pet.entity.Pet;
import java.util.List;
import java.util.Optional;

public interface PetMapper {
    Pet insert(String name, String species);
    Optional<Pet> findById(Long id);
    List<Pet> findAll();
}
