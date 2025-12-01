package com.widyu.goal.medicineschedule.dto.response;

import java.util.List;

public record MedicineHomeResponse(
        List<ScheduleItem> schedules
) {
    public record ScheduleItem(
            Long scheduleId,
            Integer medicineTotalCount,
            String alarmTime,
            List<MedicineDetail> medicineDetails
    ) {}

    public record MedicineDetail(
            String name,
            Integer count
    ) {}
}
