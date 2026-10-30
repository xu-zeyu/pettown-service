package com.pettown.app.pet.controller;

import com.pettown.common.result.Result;
import com.pettown.common.log.annotation.Log;
import com.pettown.app.pet.dto.CreatePetRequest;
import com.pettown.app.pet.vo.PetResponse;
import com.pettown.business.pet.service.PetService;
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
public class PetController {
    private final PetService petService;

    public PetController(PetService petService) {
        this.petService = petService;
    }

    @PostMapping
    @Log(value = "创建宠物", operationType = "CREATE")
    public ResponseEntity<Result<PetResponse>> createPet(@Valid @RequestBody CreatePetRequest request) {
        PetResponse pet = PetResponse.from(petService.createPet(request.name(), request.species()));
        return ResponseEntity.created(URI.create("/api/pets/" + pet.id())).body(Result.success(pet));
    }

    @GetMapping("/{id}")
    public Result<PetResponse> getPet(@PathVariable("id") Long id) {
        return Result.success(PetResponse.from(petService.getPet(id)));
    }

    @GetMapping
    public Result<List<PetResponse>> listPets() {
        return Result.success(petService.listPets().stream().map(PetResponse::from).toList());
    }
}
