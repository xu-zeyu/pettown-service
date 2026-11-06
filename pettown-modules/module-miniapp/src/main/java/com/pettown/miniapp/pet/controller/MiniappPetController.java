package com.pettown.miniapp.pet.controller;

import com.pettown.common.mybatis.PageResult;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping
    @Operation(summary = "查询我的宠物列表", description = "仅返回当前用户宠物，按创建时间倒序；非法分页参数使用默认 page=1、size=10")
    public Result<PageResult<PetProfileResponse>> list(
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        int pageNumber = validNumberOrDefault(page, 1, Integer.MAX_VALUE, 1);
        int pageSize = validNumberOrDefault(size, 1, 1000, 10);
        return Result.success(service.list(PetTownToken.USER.getLoginIdAsString(), pageNumber, pageSize));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询我的宠物详情", description = "仅返回当前用户的完整宠物档案；他人宠物与不存在的 ID 统一返回不存在")
    public Result<PetProfileResponse> detail(@PathVariable Long id) {
        return Result.success(PetProfileResponse.from(
                service.get(PetTownToken.USER.getLoginIdAsString(), id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑我的宠物档案", description = "仅归属用户可编辑；字段校验同创建，未提供的可选字段清空")
    public Result<PetProfileResponse> update(@PathVariable Long id,
            @Valid @RequestBody CreatePetProfileRequest request) {
        return Result.success(PetProfileResponse.from(
                service.update(PetTownToken.USER.getLoginIdAsString(), id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除我的宠物", description = "仅归属用户可删除；宠物及关联健康记录逻辑删除，重复删除返回不存在")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(PetTownToken.USER.getLoginIdAsString(), id);
        return Result.success();
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
