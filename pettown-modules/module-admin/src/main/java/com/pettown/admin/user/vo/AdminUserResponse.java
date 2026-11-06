package com.pettown.admin.user.vo;

import com.pettown.business.user.entity.User;
import com.pettown.business.user.entity.UserStatus;
import com.pettown.common.commonEnum.UserSourceEnum;
import java.time.LocalDateTime;

/** 不暴露密码散列的 C 端用户视图。 */
public record AdminUserResponse(String id, String phone, UserSourceEnum source,
        UserStatus status, LocalDateTime createdAt) {
    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(user.getId(), user.getPhone(), user.getSource(),
                user.getStatus(), user.getCreatedAt());
    }
}
