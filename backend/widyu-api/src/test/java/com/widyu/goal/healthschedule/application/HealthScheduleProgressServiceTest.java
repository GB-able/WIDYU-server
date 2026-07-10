package com.widyu.goal.healthschedule.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.widyu.goal.healthschedule.repository.HealthScheduleRepository;
import com.widyu.global.util.MemberUtil;
import com.widyu.healthschedule.HealthSchedule;
import com.widyu.healthschedule.ProgressStatus;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("HealthScheduleProgressService 지난 일정 마감 배치 단위 테스트")
class HealthScheduleProgressServiceTest {

    @Mock private HealthScheduleRepository healthScheduleRepository;
    @Mock private SeniorProfileRepository seniorProfileRepository;
    @Mock private FamilyMembershipRepository familyMembershipRepository;
    @Mock private MemberUtil memberUtil;

    @InjectMocks private HealthScheduleProgressService healthScheduleProgressService;

    @Captor private ArgumentCaptor<LocalDateTime> beforeDateCaptor;

    private HealthSchedule upcomingScheduleAt(LocalDateTime scheduledAt) {
        Member member = Member.createMember(MemberType.SENIOR, "부모님", "01011112222");
        return HealthSchedule.create(member, "건강검진", "서울시", 37.5, 127.0, scheduledAt);
    }

    @Test
    @DisplayName("오늘 이전 UPCOMING 일정을 여러 날치 조회해 모두 INCOMPLETE로 마감한다")
    void 오늘_이전_UPCOMING_일정을_모두_INCOMPLETE로_마감한다() {
        // given
        HealthSchedule yesterday = upcomingScheduleAt(LocalDateTime.now().minusDays(1));
        HealthSchedule threeDaysAgo = upcomingScheduleAt(LocalDateTime.now().minusDays(3));
        given(healthScheduleRepository.findByStatusAndScheduledAtBefore(
                eq(ProgressStatus.UPCOMING), beforeDateCaptor.capture()))
                .willReturn(List.of(yesterday, threeDaysAgo));

        // when
        healthScheduleProgressService.markOverdueSchedulesAsIncomplete();

        // then
        assertThat(yesterday.getProgressStatus()).isEqualTo(ProgressStatus.INCOMPLETE);
        assertThat(threeDaysAgo.getProgressStatus()).isEqualTo(ProgressStatus.INCOMPLETE);
        assertThat(beforeDateCaptor.getValue().toLocalTime())
                .isEqualTo(java.time.LocalTime.MIDNIGHT);
    }

    @Test
    @DisplayName("마감할 지난 일정이 없으면 아무 상태도 변경하지 않는다")
    void 마감할_지난_일정이_없으면_변경하지_않는다() {
        // given
        given(healthScheduleRepository.findByStatusAndScheduledAtBefore(
                eq(ProgressStatus.UPCOMING), beforeDateCaptor.capture()))
                .willReturn(List.of());

        // when
        healthScheduleProgressService.markOverdueSchedulesAsIncomplete();

        // then
        then(healthScheduleRepository).should()
                .findByStatusAndScheduledAtBefore(eq(ProgressStatus.UPCOMING), beforeDateCaptor.capture());
    }
}
