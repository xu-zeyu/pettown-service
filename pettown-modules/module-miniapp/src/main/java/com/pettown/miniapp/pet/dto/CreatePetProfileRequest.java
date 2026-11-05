package com.pettown.miniapp.pet.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CreatePetProfileRequest(
        @NotBlank(message = "宠物名称不能为空")
        @Size(max = 30, message = "宠物名称不能超过 30 字") String name,
        @NotNull(message = "宠物物种不能为空") PetSpecies species,
        @Size(max = 100, message = "品种不能超过 100 字") String breed,
        @Size(max = 16, message = "性别不能超过 16 字") String gender,
        LocalDate birthDate,
        @DecimalMin(value = "0.00", message = "体重不能小于 0")
        @DecimalMax(value = "200.00", message = "体重不能超过 200 kg")
        @Digits(integer = 3, fraction = 2, message = "体重最多保留两位小数") BigDecimal weightKg,
        Boolean sterilized,
        @Size(max = 2048, message = "头像地址过长") String avatarUrl) {
}
