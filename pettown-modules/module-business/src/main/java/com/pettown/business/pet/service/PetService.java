package com.pettown.business.pet.service;

import com.pettown.business.pet.entity.Pet;
import java.util.List;

/**
 * 宠物服务：所有读写均按归属用户（ownerId）隔离，
 * 跨用户访问与不存在的宠物返回同一 NOT_FOUND，不泄露资源存在性。
 */
public interface PetService {
    Pet createPet(String ownerId, String name, String species);
    Pet getPet(String ownerId, Long id);
    List<Pet> listPets(String ownerId);
}
