package com.widyu.goal.medicineschedule.dto.response;

import com.widyu.medicine.MedicineSchedule;
import java.util.List;
import java.util.stream.Collectors;

public record MedicineHomeResponse(
        List<ScheduleItem> medicineSchedules
) {
    public static MedicineHomeResponse of(List<ScheduleItem> medicineSchedules) {
        return new MedicineHomeResponse(medicineSchedules);
    }

    public record ScheduleItem(
            Long medicineScheduleId,
            Integer totalCount,
            String alarmTime,
            List<MedicineItem> medicines
    ) {
        public static ScheduleItem from(MedicineSchedule schedule) {
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
