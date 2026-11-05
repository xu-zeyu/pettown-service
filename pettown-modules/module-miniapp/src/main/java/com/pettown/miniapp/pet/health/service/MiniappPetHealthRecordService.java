package com.pettown.miniapp.pet.health.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pettown.business.pet.entity.Pet;
import com.pettown.business.pet.entity.PetHealthRecord;
import com.pettown.business.pet.mapper.PetHealthRecordMapper;
import com.pettown.business.pet.mapper.PetMapper;
import com.pettown.common.exception.BusinessException;
import com.pettown.common.exception.ErrorCodeEnum;
import com.pettown.common.mybatis.PageParam;
import com.pettown.common.mybatis.PageResult;
import com.pettown.miniapp.pet.health.dto.CreatePetHealthRecordRequest;
import com.pettown.miniapp.pet.health.dto.HealthRecordType;
import com.pettown.miniapp.pet.health.vo.PetHealthRecordResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MiniappPetHealthRecordService {
    private final PetMapper pets;
    private final PetHealthRecordMapper records;

    public MiniappPetHealthRecordService(PetMapper pets, PetHealthRecordMapper records) {
        this.pets = pets;
        this.records = records;
    }

    @Transactional
    public PetHealthRecord create(String ownerId, Long petId, CreatePetHealthRecordRequest request) {
        Pet pet = pets.selectOne(new LambdaQueryWrapper<Pet>()
                .eq(Pet::getId, petId)
                .eq(Pet::getOwnerId, ownerId)
                .last("FOR UPDATE"));
        if (pet == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "宠物不存在");
        }
        PetHealthRecord record = new PetHealthRecord();
        record.setPetId(pet.getId());
        record.setRecordType(request.type().name());
        record.setTitle(request.title().trim());
        record.setRecordDate(request.recordDate());
        record.setNotes(request.notes());
        record.setNextReminderDate(request.nextReminderDate());
        records.insert(record);
        return record;
    }

    @Transactional(readOnly = true)
    public PageResult<PetHealthRecordResponse> list(String ownerId, Long petId,
            String typeValue, int page, int size) {
        Pet pet = pets.selectOne(new LambdaQueryWrapper<Pet>()
                .eq(Pet::getId, petId)
                .eq(Pet::getOwnerId, ownerId));
        if (pet == null) {
            throw new BusinessException(ErrorCodeEnum.NOT_FOUND, "宠物不存在");
        }
        HealthRecordType type = parseType(typeValue);
        var query = new LambdaQueryWrapper<PetHealthRecord>()
                .eq(PetHealthRecord::getPetId, pet.getId())
                .eq(type != null, PetHealthRecord::getRecordType, type == null ? null : type.name())
                .orderByDesc(PetHealthRecord::getRecordDate)
                .orderByDesc(PetHealthRecord::getId);
        var result = records.selectPage(new PageParam().setPage(page).setSize(size), query);
        return new PageResult<>(result.getRecords().stream().map(PetHealthRecordResponse::from).toList(),
                result.getTotal());
    }

    private static HealthRecordType parseType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return HealthRecordType.valueOf(type);
        } catch (IllegalArgumentException ignored) {
            throw new BusinessException(ErrorCodeEnum.FAIL, "记录类型无效");
        }
    }
}
