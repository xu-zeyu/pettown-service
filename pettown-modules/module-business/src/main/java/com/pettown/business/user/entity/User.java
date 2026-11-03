package com.pettown.business.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.pettown.common.commonEnum.UserSourceEnum;
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
}
