package com.pettown.app.pet.vo;

import com.pettown.business.pet.entity.Pet;

public record PetResponse(Long id, String name, String species) {
    public static PetResponse from(Pet pet) {
        return new PetResponse(pet.getId(), pet.getName(), pet.getSpecies());
    }
}
