package com.pettown.miniapp.pet.health.controller;

import com.pettown.common.result.Result;
import com.pettown.common.security.config.PetTownToken;
import com.pettown.miniapp.pet.health.dto.CreatePetHealthRecordRequest;
import com.pettown.miniapp.pet.health.service.MiniappPetHealthRecordService;
import com.pettown.miniapp.pet.health.vo.PetHealthRecordResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/miniapp/pets/{petId}/health-records")
@Tag(name = "小程序宠物健康记录")
@SecurityRequirement(name = "bearerAuth")
public class MiniappPetHealthRecordController {
    private final MiniappPetHealthRecordService service;

    public MiniappPetHealthRecordController(MiniappPetHealthRecordService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "新增宠物健康记录", description = "仅宠物归属用户可新增，记录日期不能晚于今天")
    public ResponseEntity<Result<PetHealthRecordResponse>> create(@PathVariable Long petId,
            @Valid @RequestBody CreatePetHealthRecordRequest request) {
        var record = PetHealthRecordResponse.from(
                service.create(PetTownToken.USER.getLoginIdAsString(), petId, request));
        return ResponseEntity.created(URI.create("/api/miniapp/pets/" + petId + "/health-records/" + record.id()))
                .body(Result.success(record));
    }
}
