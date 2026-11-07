package com.pettown.miniapp.user.vo;

import com.pettown.common.commonEnum.UserSourceEnum;
import java.time.LocalDateTime;

public record UserInfoResponse(String userId, String phone, String nickname, String avatarUrl,
                               UserSourceEnum source, LocalDateTime registeredTime) {
}
