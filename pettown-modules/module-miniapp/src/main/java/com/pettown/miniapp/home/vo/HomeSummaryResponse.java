package com.pettown.miniapp.home.vo;

import java.util.List;

public record HomeSummaryResponse(int petCount, List<PetCard> petCards, long upcomingReminderCount) {
    public record PetCard(Long id, String name, String species, String avatarUrl) {
    }
}
