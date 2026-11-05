package com.pettown.miniapp.pet.health.controller;

import com.pettown.common.result.Result;
import com.pettown.common.mybatis.PageResult;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping
    @Operation(summary = "查询宠物健康记录", description = "仅宠物归属用户可查看，按记录日期倒序，支持类型筛选和分页")
    public Result<PageResult<PetHealthRecordResponse>> list(@PathVariable Long petId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        return Result.success(service.list(PetTownToken.USER.getLoginIdAsString(), petId,
                type, validNumberOrDefault(page, 1, Integer.MAX_VALUE, 1),
                validNumberOrDefault(size, 1, 1000, 10)));
    }

    private static int validNumberOrDefault(String value, int min, int max, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= min && parsed <= max ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
