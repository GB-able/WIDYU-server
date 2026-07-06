package com.widyu.goal.medicineschedule.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 선택한 날짜 기준 개별 약 스케줄의 복용 상태.
 * 인증은 알람 시간 전후 {@link #ALLOWED_WINDOW_MINUTES}분 이내에만 가능하다.
 */
public enum MedicationStatus {
    DONE,       // 복용 인증 완료
    AVAILABLE,  // 지금 인증 가능 (알람 시간 ±30분 이내)
    UPCOMING,   // 아직 인증 시간 전 (복용 시간이 안 됨)
    MISSED;     // 인증 시간이 지났는데 미인증

    public static final int ALLOWED_WINDOW_MINUTES = 30;

    public static MedicationStatus of(boolean verified, LocalDate date, LocalTime alarmTime, LocalDateTime now) {
        if (verified) {
            return DONE;
        }

        LocalDateTime alarmDateTime = date.atTime(alarmTime);
        LocalDateTime windowStart = alarmDateTime.minusMinutes(ALLOWED_WINDOW_MINUTES);
        LocalDateTime windowEnd = alarmDateTime.plusMinutes(ALLOWED_WINDOW_MINUTES);

        if (now.isBefore(windowStart)) {
            return UPCOMING;
        }

        if (now.isAfter(windowEnd)) {
            return MISSED;
        }

        return AVAILABLE;
    }
}
