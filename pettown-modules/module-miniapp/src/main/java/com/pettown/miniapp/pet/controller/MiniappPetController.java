package com.pettown.miniapp.pet.controller;

import com.pettown.common.result.Result;
import com.pettown.common.security.config.PetTownToken;
import com.pettown.miniapp.pet.dto.CreatePetProfileRequest;
import com.pettown.miniapp.pet.service.MiniappPetProfileService;
import com.pettown.miniapp.pet.vo.PetProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/miniapp/pets")
@Tag(name = "小程序宠物档案")
@SecurityRequirement(name = "bearerAuth")
public class MiniappPetController {
    private final MiniappPetProfileService service;

    public MiniappPetController(MiniappPetProfileService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "创建宠物档案", description = "当前登录用户最多创建 20 只；物种为 CAT、DOG 或 OTHER")
    public ResponseEntity<Result<PetProfileResponse>> create(@Valid @RequestBody CreatePetProfileRequest request) {
        var profile = PetProfileResponse.from(service.create(PetTownToken.USER.getLoginIdAsString(), request));
        return ResponseEntity.created(URI.create("/api/miniapp/pets/" + profile.id())).body(Result.success(profile));
    }
}
