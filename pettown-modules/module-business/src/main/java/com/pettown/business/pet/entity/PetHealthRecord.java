package com.pettown.business.pet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.pettown.common.mybatis.model.BaseEntity;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pet_health_record")
public class PetHealthRecord extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long petId;
    private String recordType;
    private String title;
    private LocalDate recordDate;
    private String notes;
    private LocalDate nextReminderDate;
}
