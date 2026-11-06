package com.pettown.miniapp.user.controller;

import com.pettown.business.user.entity.User;
import com.pettown.business.user.mapper.UserMapper;
import com.pettown.common.result.Result;
import com.pettown.common.security.config.PetTownToken;
import com.pettown.miniapp.user.dto.UpdateProfileRequest;
import com.pettown.miniapp.user.service.UserProfileService;
import com.pettown.miniapp.user.vo.UserInfoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/miniapp/user")
@Tag(name = "小程序登录管理")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    @Resource
    private UserMapper userMapper;
    @Resource
    private UserProfileService userProfileService;


    @GetMapping("/me")
    @Operation(summary = "获取当前用户信息")
    public Result<UserInfoResponse> me() {
        PetTownToken.USER.checkLogin();
        User user = userMapper.selectById(PetTownToken.USER.getLoginIdAsString());
        return Result.success(new UserInfoResponse(user.getId(), user.getPhone(),
                user.getNickname(), user.getAvatarUrl()));
    }

    @PutMapping("/profile")
    @Operation(summary = "修改当前用户资料（昵称/头像）")
    public Result<UserInfoResponse> updateProfile(@RequestBody UpdateProfileRequest request) {
        String userId = PetTownToken.USER.getLoginIdAsString();
        return Result.success(userProfileService.update(userId, request.nickname(), request.avatarUrl()));
    }
}
