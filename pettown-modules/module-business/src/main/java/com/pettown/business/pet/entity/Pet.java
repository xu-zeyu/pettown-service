package com.pettown.business.pet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.pettown.common.mybatis.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("pet")
public class Pet extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String species;
    /** 归属用户 ID（user_account.id），宠物数据按归属用户隔离。 */
    private String ownerId;
    private String breed;
    private String gender;
    private LocalDate birthDate;
    private BigDecimal weightKg;
    private Boolean sterilized;
    private String avatarUrl;

    /** 兼容 App 端已有的基础宠物创建路径。 */
    public Pet(Long id, String name, String species, String ownerId) {
        this.id = id;
        this.name = name;
        this.species = species;
        this.ownerId = ownerId;
    }
}
