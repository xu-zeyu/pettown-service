package com.pettown.miniapp.pet.health.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreatePetHealthRecordRequest(
        @NotNull(message = "记录类型不能为空") HealthRecordType type,
        @NotBlank(message = "记录标题不能为空")
        @Size(max = 100, message = "记录标题不能超过 100 字") String title,
        @NotNull(message = "记录日期不能为空")
        @PastOrPresent(message = "记录日期不能晚于今天") LocalDate recordDate,
        @Size(max = 2000, message = "备注不能超过 2000 字") String notes,
        LocalDate nextReminderDate) {
}
