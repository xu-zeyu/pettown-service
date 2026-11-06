package com.pettown.business.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.pettown.common.commonEnum.UserSourceEnum;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_account")
public class User {
    @TableId(type = IdType.INPUT)
    private String id;
    private String phone;
    private String passwordHash;
    private UserSourceEnum source;
    private UserStatus status = UserStatus.ACTIVE;
    private String nickname;
    @TableField("avatar")
    private String avatarUrl;
    private LocalDateTime createdAt;

    public User(String id, String phone, String passwordHash, UserSourceEnum source) {
        this.id = id;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.source = source;
        this.status = UserStatus.ACTIVE;
    }
}
