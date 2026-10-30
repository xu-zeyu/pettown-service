package com.pettown.business.pet.service;

import com.pettown.business.pet.entity.Pet;
import java.util.List;

public interface PetService {
    Pet createPet(String name, String species);
    Pet getPet(Long id);
    List<Pet> listPets();
}
