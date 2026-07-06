package com.widyu.goal.medicineschedule.dto.response;

import com.widyu.medicine.MedicineSchedule;
import java.util.List;
import java.util.stream.Collectors;

public record MedicineHomeResponse(
        List<ScheduleItem> schedules
) {
    public static MedicineHomeResponse of(List<ScheduleItem> schedules) {
        return new MedicineHomeResponse(schedules);
    }

    public record ScheduleItem(
            Long scheduleId,
            Integer medicineTotalCount,
            String alarmTime,
            List<MedicineDetail> medicineDetails
    ) {
        public static ScheduleItem from(MedicineSchedule schedule) {
            List<MedicineDetail> medicineDetails = schedule.getCategories().stream()
                    .flatMap(category -> category.getMedicines().stream()
                            .map(detail -> MedicineDetail.of(
                                    detail.getMedicine().getName(),
                                    detail.getDose()
                            )))
                    .collect(Collectors.toList());

            return new ScheduleItem(
                    schedule.getId(),
                    schedule.getTotalCount(),
                    schedule.getAlarmTime().toString(),
                    medicineDetails
            );
        }
    }

    public record MedicineDetail(
            String name,
            Integer count
    ) {
        public static MedicineDetail of(String name, Integer count) {
            return new MedicineDetail(name, count);
        }
    }
}
