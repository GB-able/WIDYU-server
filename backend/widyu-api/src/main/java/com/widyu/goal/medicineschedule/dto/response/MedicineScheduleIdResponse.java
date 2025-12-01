package com.widyu.goal.medicineschedule.dto.response;

public record MedicineScheduleIdResponse(
        Long medicineScheduleId
) {
    public static MedicineScheduleIdResponse of(Long medicineScheduleId) {
        return new MedicineScheduleIdResponse(medicineScheduleId);
    }
}
