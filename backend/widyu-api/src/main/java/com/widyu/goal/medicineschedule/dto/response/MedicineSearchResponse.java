package com.widyu.goal.medicineschedule.dto.response;

import java.util.List;

public record MedicineSearchResponse(
        List<MedicineItem> medicines
) {
    public record MedicineItem(
            Long medicineId,
            String itemName,
            String itemImage,
            String usage,
            String efficacy

    ) {}
}
