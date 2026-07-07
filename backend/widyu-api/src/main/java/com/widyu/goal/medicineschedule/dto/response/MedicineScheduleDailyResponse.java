package com.widyu.goal.medicineschedule.dto.response;

import com.widyu.medicine.MedicineSchedule;
import java.util.List;
import java.util.stream.Collectors;

public record MedicineScheduleDailyResponse(
        List<ScheduleItem> medicineSchedules
) {
    public static MedicineScheduleDailyResponse of(List<ScheduleItem> medicineSchedules) {
        return new MedicineScheduleDailyResponse(medicineSchedules);
    }

    public record ScheduleItem(
            Long medicineScheduleId,
            Integer totalCount,
            String alarmTime,
            MedicationStatus status,
            String proofImageUrl,
            List<MedicineItem> medicines
    ) {
        public static ScheduleItem from(MedicineSchedule schedule, MedicationStatus status, String proofImageUrl) {
            List<MedicineItem> medicines = schedule.getCategories().stream()
                    .flatMap(category -> category.getMedicines().stream()
                            .map(detail -> MedicineItem.of(
                                    detail.getMedicine().getName(),
                                    detail.getDose()
                            )))
                    .collect(Collectors.toList());

            return new ScheduleItem(
                    schedule.getId(),
                    schedule.getTotalCount(),
                    schedule.getAlarmTime().toString(),
                    status,
                    proofImageUrl,
                    medicines
            );
        }
    }

    public record MedicineItem(
            String name,
            Integer count
    ) {
        public static MedicineItem of(String name, Integer count) {
            return new MedicineItem(name, count);
        }
    }
}
