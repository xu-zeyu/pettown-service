package com.pettown.miniapp.home.controller;

import com.pettown.common.result.Result;
import com.pettown.common.security.config.PetTownToken;
import com.pettown.miniapp.home.service.MiniappHomeService;
import com.pettown.miniapp.home.vo.HomeSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/miniapp/home")
@Tag(name = "小程序首页")
@SecurityRequirement(name = "bearerAuth")
public class MiniappHomeController {
    private final MiniappHomeService homeService;

    public MiniappHomeController(MiniappHomeService homeService) {
        this.homeService = homeService;
    }

    @GetMapping("/summary")
    @Operation(summary = "首页聚合概览（宠物数量/卡片/未来7天提醒数）")
    public Result<HomeSummaryResponse> summary() {
        return Result.success(homeService.summary(PetTownToken.USER.getLoginIdAsString()));
    }
}
