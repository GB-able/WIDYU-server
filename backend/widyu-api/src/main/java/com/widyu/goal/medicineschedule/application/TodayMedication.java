package com.widyu.goal.medicineschedule.application;

import com.widyu.goal.medicineschedule.dto.response.MedicationStatus;
import com.widyu.medicine.MedicationProof;
import com.widyu.medicine.MedicineSchedule;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 오늘 복약 현황. 오늘 유효한 일정(알람 시각 오름차순)과 일정 ID별 대표 인증(가장 먼저 한 인증)이다.
 * 오늘 유효 목록에 없는 일정의 인증은 담지 않는다.
 */
public record TodayMedication(List<MedicineSchedule> schedules, Map<Long, MedicationProof> proofsByScheduleId) {

    public MedicineSchedule nextSchedule(LocalTime now) {
        return findNextSchedule(schedules, now, proofsByScheduleId.keySet());
    }

    // 알람 시간 오름차순 목록에서 인증 마감 전인 첫 일정을 노출하고, 모두 마감됐으면 마지막 일정을 유지한다.
    // 목표 홈과 시니어 홈 카드가 이 함수를 쓴다. 앱은 고른 일정 ID로 인증하므로 두 화면의 기준이 같아야 한다 (#962).
    // 오늘 이미 인증한 일정은 건너뛴다. 인증창이 겹칠 때 인증한 일정에 머물러 다음 일정을 막지 않게 한다 (#964).
    public static MedicineSchedule findNextSchedule(List<MedicineSchedule> schedules, LocalTime now,
                                                    Set<Long> verifiedScheduleIds) {
        return schedules.stream()
                .filter(s -> !verifiedScheduleIds.contains(s.getId()))
                // 시간 차이로 비교해 자정을 넘는 마감 시각이 LocalTime에서 순환하지 않도록 한다.
                .filter(s -> Duration.between(s.getAlarmTime(), now)
                        .compareTo(Duration.ofMinutes(MedicationStatus.ALLOWED_WINDOW_MINUTES)) <= 0)
                .min(Comparator.comparing(MedicineSchedule::getAlarmTime))
                .orElse(schedules.getLast());
    }
}
