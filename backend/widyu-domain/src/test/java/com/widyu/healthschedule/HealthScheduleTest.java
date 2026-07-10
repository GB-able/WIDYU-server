package com.widyu.healthschedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.widyu.member.Member;
import com.widyu.member.MemberType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("HealthSchedule 표시용 진행상태 단위 테스트")
class HealthScheduleTest {

    private HealthSchedule scheduleAt(LocalDateTime scheduledAt) {
        Member member = Member.createMember(MemberType.SENIOR, "부모님", "01011112222");
        return HealthSchedule.create(member, "건강검진", "서울시", 37.5, 127.0, scheduledAt);
    }

    @Test
    @DisplayName("인증 가능 시간이 지난 UPCOMING 일정을 조회하면 INCOMPLETE를 반환한다")
    void 인증가능시간이_지난_UPCOMING_일정은_INCOMPLETE를_반환한다() {
        // given
        LocalDateTime scheduledAt = LocalDateTime.of(2026, 7, 10, 14, 0);
        HealthSchedule schedule = scheduleAt(scheduledAt);

        // when & then
        assertThat(schedule.getDisplayProgressStatus(scheduledAt.plusMinutes(31)))
                .isEqualTo(ProgressStatus.INCOMPLETE);
    }

    @Test
    @DisplayName("일정 시간 30분까지는 UPCOMING을 그대로 반환한다")
    void 일정시간_30분까지는_UPCOMING을_유지한다() {
        // given
        LocalDateTime scheduledAt = LocalDateTime.of(2026, 7, 10, 14, 0);
        HealthSchedule schedule = scheduleAt(scheduledAt);

        // when & then
        assertThat(schedule.getDisplayProgressStatus(scheduledAt.plusMinutes(30)))
                .isEqualTo(ProgressStatus.UPCOMING);
    }

    @Test
    @DisplayName("예정일이 오늘이고 인증 가능 시간이 남았으면 UPCOMING을 그대로 반환한다")
    void 예정일이_오늘이고_인증가능시간이_남았으면_UPCOMING을_유지한다() {
        // given
        LocalDateTime scheduledAt = LocalDateTime.of(2026, 7, 10, 14, 0);
        HealthSchedule schedule = scheduleAt(scheduledAt);

        // when & then
        assertThat(schedule.getDisplayProgressStatus(scheduledAt.toLocalDate().atStartOfDay()))
                .isEqualTo(ProgressStatus.UPCOMING);
    }

    @Test
    @DisplayName("예정일이 미래인 UPCOMING 일정을 조회하면 UPCOMING을 그대로 반환한다")
    void 예정일이_미래인_UPCOMING_일정은_UPCOMING을_유지한다() {
        // given
        HealthSchedule schedule = scheduleAt(LocalDate.now().plusDays(2).atTime(9, 0));

        // when & then
        assertThat(schedule.getDisplayProgressStatus()).isEqualTo(ProgressStatus.UPCOMING);
    }

    @Test
    @DisplayName("예정일이 지났어도 COMPLETED 일정은 COMPLETED를 그대로 반환한다")
    void 완료된_일정은_예정일이_지나도_COMPLETED를_유지한다() {
        // given
        HealthSchedule schedule = scheduleAt(LocalDate.now().minusDays(2).atTime(9, 0));
        schedule.complete();

        // when & then
        assertThat(schedule.getDisplayProgressStatus()).isEqualTo(ProgressStatus.COMPLETED);
    }

    @Test
    @DisplayName("이미 INCOMPLETE로 마감된 일정은 INCOMPLETE를 그대로 반환한다")
    void 이미_INCOMPLETE인_일정은_INCOMPLETE를_유지한다() {
        // given
        HealthSchedule schedule = scheduleAt(LocalDate.now().minusDays(2).atTime(9, 0));
        schedule.markIncomplete();

        // when & then
        assertThat(schedule.getDisplayProgressStatus()).isEqualTo(ProgressStatus.INCOMPLETE);
    }

    @Test
    @DisplayName("방문 인증은 일정 당일 00시부터 허용한다")
    void 방문_인증은_일정_당일_자정부터_허용한다() {
        // given
        LocalDate scheduleDate = LocalDate.of(2026, 7, 10);
        HealthSchedule schedule = scheduleAt(scheduleDate.atTime(14, 0));

        // when & then
        assertThat(schedule.canCompleteAt(scheduleDate.atStartOfDay())).isTrue();
    }

    @Test
    @DisplayName("방문 인증은 일정 당일 전에는 허용하지 않는다")
    void 방문_인증은_일정_당일_전에는_허용하지_않는다() {
        // given
        LocalDate scheduleDate = LocalDate.of(2026, 7, 10);
        HealthSchedule schedule = scheduleAt(scheduleDate.atTime(14, 0));

        // when & then
        assertThat(schedule.canCompleteAt(scheduleDate.minusDays(1).atTime(23, 59))).isFalse();
    }

    @Test
    @DisplayName("방문 인증은 일정 시간 30분 후까지 허용한다")
    void 방문_인증은_일정시간_30분_후까지_허용한다() {
        // given
        LocalDateTime scheduledAt = LocalDateTime.of(2026, 7, 10, 14, 0);
        HealthSchedule schedule = scheduleAt(scheduledAt);

        // when & then
        assertThat(schedule.canCompleteAt(scheduledAt.plusMinutes(30))).isTrue();
    }

    @Test
    @DisplayName("방문 인증은 일정 시간 30분 후를 지나면 허용하지 않는다")
    void 방문_인증은_일정시간_30분_후를_지나면_허용하지_않는다() {
        // given
        LocalDateTime scheduledAt = LocalDateTime.of(2026, 7, 10, 14, 0);
        HealthSchedule schedule = scheduleAt(scheduledAt);

        // when & then
        assertThat(schedule.canCompleteAt(scheduledAt.plusMinutes(31))).isFalse();
    }
}
