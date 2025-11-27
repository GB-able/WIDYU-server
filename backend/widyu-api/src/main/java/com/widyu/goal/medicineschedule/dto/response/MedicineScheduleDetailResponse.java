package com.widyu.goal.medicineschedule.dto.response;

import java.util.List;

public record MedicineScheduleDetailResponse(
        String alarmTime,
        Double totalCount,
        List<CategoryItem> categories
) {
    public record CategoryItem(
            Long categoryId,
            String name,
            Double countSum,
            List<MedicineItem> medicines
    ) {}

    public record MedicineItem(
            Long medicineId,
            String medicineName,
            Double dose,
            String imageUrl,
            String description
    ) {}
}
