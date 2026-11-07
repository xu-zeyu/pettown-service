package com.pettown.admin.user.vo;

import com.pettown.business.pet.entity.Pet;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * C 端用户宠物数据视图：条目字段与移动端 PetProfileResponse 一致，
 * 不含 passwordHash 等用户敏感字段与健康记录明细。
 */
public record AdminUserPetsResponse(String userId, Long petCount, List<PetItem> pets) {

    public static AdminUserPetsResponse from(String userId, List<Pet> pets) {
        return new AdminUserPetsResponse(userId, (long) pets.size(),
                pets.stream().map(PetItem::from).toList());
    }

    /** 与移动端 PetProfileResponse 同字段，保证管理端与移动端数据一致。 */
    public record PetItem(Long id, String ownerId, String name, String species, String breed,
            String gender, LocalDate birthDate, BigDecimal weightKg, Boolean sterilized,
            String avatarUrl, LocalDateTime createdTime, LocalDateTime updatedTime) {
        public static PetItem from(Pet pet) {
            return new PetItem(pet.getId(), pet.getOwnerId(), pet.getName(), pet.getSpecies(),
                    pet.getBreed(), pet.getGender(), pet.getBirthDate(), pet.getWeightKg(),
                    pet.getSterilized(), pet.getAvatarUrl(), pet.getCreatedTime(), pet.getUpdatedTime());
        }
    }
}
