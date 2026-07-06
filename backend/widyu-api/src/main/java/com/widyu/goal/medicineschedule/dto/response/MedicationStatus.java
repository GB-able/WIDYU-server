package com.widyu.goal.medicineschedule.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 선택한 날짜 기준 개별 약 스케줄의 복용 상태.
 * 인증은 알람 시간 전후 {@link #ALLOWED_WINDOW_MINUTES}분 이내에만 가능하며,
 * 인증 마감(알람 + {@link #ALLOWED_WINDOW_MINUTES}분)이 지나면 더 이상 인증할 수 없다.
 */
public enum MedicationStatus {
    DONE,       // 복용 인증 완료
    UPCOMING,   // 미인증 + 인증 마감 전 (아직 복용 시간이 안 됨)
    MISSED;     // 미인증 + 인증 마감이 지남 (놓침)

    public static final int ALLOWED_WINDOW_MINUTES = 30;

    public static MedicationStatus of(boolean verified, LocalDate date, LocalTime alarmTime, LocalDateTime now) {
        if (verified) {
            return DONE;
        }

        LocalDateTime verificationDeadline = date.atTime(alarmTime).plusMinutes(ALLOWED_WINDOW_MINUTES);

        if (now.isAfter(verificationDeadline)) {
            return MISSED;
        }

        return UPCOMING;
    }
}
