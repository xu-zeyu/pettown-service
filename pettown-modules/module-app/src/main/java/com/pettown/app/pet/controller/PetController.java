package com.pettown.app.pet.controller;

import com.pettown.common.result.Result;
import com.pettown.common.log.annotation.Log;
import com.pettown.common.security.config.PetTownToken;
import com.pettown.app.pet.dto.CreatePetRequest;
import com.pettown.app.pet.vo.PetResponse;
import com.pettown.business.pet.service.PetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pets")
@Tag(name = "App 宠物管理")
@SecurityRequirement(name = "bearerAuth")
public class PetController {
    private final PetService petService;

    public PetController(PetService petService) {
        this.petService = petService;
    }

    @PostMapping
    @Operation(summary = "创建宠物", description = "需要用户登录，创建成功返回宠物信息及资源地址；宠物归属当前登录用户")
    @Log(value = "创建宠物", operationType = "CREATE")
    public ResponseEntity<Result<PetResponse>> createPet(@Valid @RequestBody CreatePetRequest request) {
        PetResponse pet = PetResponse.from(petService.createPet(
                PetTownToken.USER.getLoginIdAsString(), request.name(), request.species()));
        return ResponseEntity.created(URI.create("/api/pets/" + pet.id())).body(Result.success(pet));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取宠物详情", description = "需要用户登录，仅返回当前登录用户自己的宠物；访问他人宠物与不存在的宠物返回同一 404")
    public Result<PetResponse> getPet(@PathVariable("id") Long id) {
        return Result.success(PetResponse.from(petService.getPet(PetTownToken.USER.getLoginIdAsString(), id)));
    }

    @GetMapping
    @Operation(summary = "查询宠物列表", description = "需要用户登录，仅返回当前登录用户自己的宠物")
    public Result<List<PetResponse>> listPets() {
        return Result.success(petService.listPets(PetTownToken.USER.getLoginIdAsString())
                .stream().map(PetResponse::from).toList());
    }
}
