package com.widyu.goal.medicineschedule.dto.response;

import java.util.List;

public record MedicineSearchResponse(
        List<MedicineItem> medicines
) {
    public record MedicineItem(
            Long medicineId,
            String medicineName,
            String imageUrl,
            String description,
            String usage,
            String efficacy
    ) {}
}
