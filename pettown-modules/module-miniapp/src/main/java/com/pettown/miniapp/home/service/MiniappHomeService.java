package com.pettown.miniapp.home.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pettown.business.pet.entity.Pet;
import com.pettown.business.pet.entity.PetHealthRecord;
import com.pettown.business.pet.mapper.PetHealthRecordMapper;
import com.pettown.business.pet.mapper.PetMapper;
import com.pettown.miniapp.home.vo.HomeSummaryResponse;
import com.pettown.miniapp.home.vo.HomeSummaryResponse.PetCard;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 首页聚合概览：宠物数量、卡片摘要与未来 7 天健康提醒数，均按归属用户隔离。 */
@Service
public class MiniappHomeService {
    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    /** 提醒窗口为今天起 7 天（含今天，即 [今天, 今天+6]）；口径待产品确认。 */
    static final int REMINDER_WINDOW_DAYS = 6;

    private final PetMapper pets;
    private final PetHealthRecordMapper healthRecords;

    @Autowired
    public MiniappHomeService(PetMapper pets, PetHealthRecordMapper healthRecords) {
        this.pets = pets;
        this.healthRecords = healthRecords;
    }

    public HomeSummaryResponse summary(String userId) {
        List<Pet> owned = pets.selectList(new LambdaQueryWrapper<Pet>()
                .eq(Pet::getOwnerId, userId)
                .orderByAsc(Pet::getId));
        List<PetCard> cards = owned.stream()
                .map(pet -> new PetCard(pet.getId(), pet.getName(), pet.getSpecies(), pet.getAvatarUrl()))
                .toList();
        long upcomingReminders = 0;
        if (!owned.isEmpty()) {
            List<Long> petIds = owned.stream().map(Pet::getId).toList();
            LocalDate today = LocalDate.now(ZONE);
            upcomingReminders = healthRecords.selectCount(new LambdaQueryWrapper<PetHealthRecord>()
                    .in(PetHealthRecord::getPetId, petIds)
                    .ge(PetHealthRecord::getNextReminderDate, today)
                    .le(PetHealthRecord::getNextReminderDate, today.plusDays(REMINDER_WINDOW_DAYS)));
        }
        return new HomeSummaryResponse(owned.size(), cards, upcomingReminders);
    }
}
