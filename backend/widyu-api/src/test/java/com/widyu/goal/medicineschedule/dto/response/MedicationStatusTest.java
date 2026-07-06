package com.widyu.goal.medicineschedule.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MedicationStatus 상태 계산 단위 테스트")
class MedicationStatusTest {

    private final LocalDate date = LocalDate.of(2026, 7, 6);
    private final LocalTime alarmTime = LocalTime.of(9, 0);

    @Test
    @DisplayName("복용 인증이 있으면 시간과 무관하게 DONE을 반환한다")
    void 인증이_있으면_DONE을_반환한다() {
        // given
        LocalDateTime now = date.atTime(3, 0);

        // when
        MedicationStatus status = MedicationStatus.of(true, date, alarmTime, now);

        // then
        assertThat(status).isEqualTo(MedicationStatus.DONE);
    }

    @Test
    @DisplayName("인증이 없고 알람 30분 전보다 이르면 UPCOMING을 반환한다")
    void 인증이_없고_인증창_전이면_UPCOMING을_반환한다() {
        // given
        LocalDateTime now = date.atTime(8, 0);

        // when
        MedicationStatus status = MedicationStatus.of(false, date, alarmTime, now);

        // then
        assertThat(status).isEqualTo(MedicationStatus.UPCOMING);
    }

    @Test
    @DisplayName("인증이 없고 알람 전후 30분 이내이면 AVAILABLE을 반환한다")
    void 인증이_없고_인증창_안이면_AVAILABLE을_반환한다() {
        // given
        LocalDateTime now = date.atTime(9, 10);

        // when
        MedicationStatus status = MedicationStatus.of(false, date, alarmTime, now);

        // then
        assertThat(status).isEqualTo(MedicationStatus.AVAILABLE);
    }

    @Test
    @DisplayName("인증이 없고 알람 30분 후를 지나면 MISSED를 반환한다")
    void 인증이_없고_인증창_후면_MISSED를_반환한다() {
        // given
        LocalDateTime now = date.atTime(10, 0);

        // when
        MedicationStatus status = MedicationStatus.of(false, date, alarmTime, now);

        // then
        assertThat(status).isEqualTo(MedicationStatus.MISSED);
    }
}
