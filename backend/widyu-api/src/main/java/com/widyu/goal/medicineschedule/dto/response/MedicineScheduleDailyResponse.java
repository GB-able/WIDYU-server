package com.widyu.goal.medicineschedule.dto.response;

import com.widyu.medicine.MedicineSchedule;
import java.util.List;
import java.util.stream.Collectors;

public record MedicineScheduleDailyResponse(
        List<ScheduleItem> medicineSchedule
) {
    public static MedicineScheduleDailyResponse of(List<ScheduleItem> medicineSchedule) {
        return new MedicineScheduleDailyResponse(medicineSchedule);
    }

    public record ScheduleItem(
            Long medicineScheduleId,
            Integer totalCount,
            String alarmTime,
            boolean taken,
            List<MedicineItem> medicines
    ) {
        public static ScheduleItem from(MedicineSchedule schedule, boolean taken) {
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
                    taken,
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
