package com.pettown.business.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.pettown.common.mybatis.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("admin_account")
public class AdminAccount extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String displayName;
    @ToString.Exclude
    private String passwordHash;
    private Boolean enabled;
    private Boolean superAdmin;
    private Boolean passwordChangeRequired;
    private Long sessionVersion;
}
