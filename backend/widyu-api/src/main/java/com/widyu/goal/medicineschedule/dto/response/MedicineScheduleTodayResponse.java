package com.widyu.goal.medicineschedule.dto.response;

import java.util.List;

public record MedicineScheduleTodayResponse(
        List<ScheduleItem> medicineSchedule
) {
    public record ScheduleItem(
            Long medicineScheduleId,
            Integer totalCount,
            String alarmTime,
            List<MedicineItem> medicines
    ) {}

    public record MedicineItem(
            String name,
            Integer count
    ) {}
}
