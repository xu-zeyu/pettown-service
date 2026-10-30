package com.pettown.app.pet.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePetRequest(
        @NotBlank(message = "宠物名称不能为空") String name,
        @NotBlank(message = "宠物种类不能为空") String species) {
}
