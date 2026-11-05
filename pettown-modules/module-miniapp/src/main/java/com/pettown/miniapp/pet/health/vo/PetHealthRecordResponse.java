package com.pettown.miniapp.pet.health.vo;

import com.pettown.business.pet.entity.PetHealthRecord;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PetHealthRecordResponse(Long id, Long petId, String type, String title,
        LocalDate recordDate, String notes, LocalDate nextReminderDate,
        LocalDateTime createdTime, LocalDateTime updatedTime) {
    public static PetHealthRecordResponse from(PetHealthRecord record) {
        return new PetHealthRecordResponse(record.getId(), record.getPetId(), record.getRecordType(),
                record.getTitle(), record.getRecordDate(), record.getNotes(),
                record.getNextReminderDate(), record.getCreatedTime(), record.getUpdatedTime());
    }
}
