package com.widyu.goal.medicineschedule.dto.response;

import com.widyu.medicine.MedicineSchedule;
import java.time.LocalDate;
import java.util.List;

public record MedicationAlarmSyncResponse(
        long revision,
        String timeZone,
        List<Schedule> schedules,
        List<String> completed
) {
    public static MedicationAlarmSyncResponse of(
            long revision, String timeZone, List<MedicineSchedule> schedules, List<String> completed) {
        return new MedicationAlarmSyncResponse(revision, timeZone,
                schedules.stream().map(Schedule::from).toList(), completed);
    }

    /**
     * 알람 한 건. 앱은 {@code effectiveFrom ≤ 날짜 ≤ effectiveTo}인 날에만 알람을 건다.
     * {@code effectiveTo}가 null이면 끝나는 날이 없다(LLD-0125).
     */
    public record Schedule(Long scheduleId, String alarmTime, Double doseCount,
                           LocalDate effectiveFrom, LocalDate effectiveTo) {
        static Schedule from(MedicineSchedule schedule) {
            return new Schedule(schedule.getId(), schedule.getAlarmTime().toString(), schedule.getTotalCount(),
                    schedule.getEffectiveFrom(), schedule.getEffectiveTo());
        }
    }
}
