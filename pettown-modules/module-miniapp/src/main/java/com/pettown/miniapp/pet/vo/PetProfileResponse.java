package com.pettown.miniapp.pet.vo;

import com.pettown.business.pet.entity.Pet;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PetProfileResponse(Long id, String ownerId, String name, String species, String breed,
        String gender, LocalDate birthDate, BigDecimal weightKg, Boolean sterilized,
        String avatarUrl, LocalDateTime createdTime, LocalDateTime updatedTime) {
    public static PetProfileResponse from(Pet pet) {
        return new PetProfileResponse(pet.getId(), pet.getOwnerId(), pet.getName(), pet.getSpecies(),
                pet.getBreed(), pet.getGender(), pet.getBirthDate(), pet.getWeightKg(),
                pet.getSterilized(), pet.getAvatarUrl(), pet.getCreatedTime(), pet.getUpdatedTime());
    }
}
