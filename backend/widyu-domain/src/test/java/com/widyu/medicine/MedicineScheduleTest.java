package com.widyu.medicine;

import static org.assertj.core.api.Assertions.assertThat;

import com.widyu.member.Member;
import com.widyu.member.MemberType;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("MedicineSchedule 유효 기간 단위 테스트")
class MedicineScheduleTest {

    private MedicineSchedule scheduleEffectiveBetween(LocalDate from, LocalDate to) {
        Member member = Member.createMember(MemberType.SENIOR, "부모님", "01011112222");
        MedicineSchedule schedule = MedicineSchedule.create(member, LocalTime.of(9, 0));
        ReflectionTestUtils.setField(schedule, "effectiveFrom", from);
        ReflectionTestUtils.setField(schedule, "effectiveTo", to);
        return schedule;
    }

    @Test
    @DisplayName("종료일이 없으면 시작일 이후의 모든 날짜에 유효하다")
    void 종료일이_없으면_시작일_이후_모두_유효하다() {
        // given
        MedicineSchedule schedule = scheduleEffectiveBetween(LocalDate.of(2026, 7, 1), null);

        // when & then
        assertThat(schedule.isEffectiveOn(LocalDate.of(2026, 6, 30))).isFalse();
        assertThat(schedule.isEffectiveOn(LocalDate.of(2026, 7, 1))).isTrue();
        assertThat(schedule.isEffectiveOn(LocalDate.of(2026, 12, 31))).isTrue();
    }

    @Test
    @DisplayName("마감된 버전은 종료일까지만 유효하고 그 이후에는 유효하지 않다")
    void 마감된_버전은_종료일_이후에는_유효하지_않다() {
        // given
        MedicineSchedule schedule = scheduleEffectiveBetween(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 10));

        // when & then
        assertThat(schedule.isEffectiveOn(LocalDate.of(2026, 7, 10))).isTrue();
        assertThat(schedule.isEffectiveOn(LocalDate.of(2026, 7, 11))).isFalse();
    }

    @Test
    @DisplayName("closeAsOf로 마감하면 지정한 날짜까지만 유효하다")
    void 마감하면_지정_날짜까지만_유효하다() {
        // given
        MedicineSchedule schedule = scheduleEffectiveBetween(LocalDate.of(2026, 7, 1), null);

        // when
        schedule.closeAsOf(LocalDate.of(2026, 7, 5));

        // then
        assertThat(schedule.isEffectiveOn(LocalDate.of(2026, 7, 5))).isTrue();
        assertThat(schedule.isEffectiveOn(LocalDate.of(2026, 7, 6))).isFalse();
    }
}
