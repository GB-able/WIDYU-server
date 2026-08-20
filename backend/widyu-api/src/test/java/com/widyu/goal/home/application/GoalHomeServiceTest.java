package com.widyu.goal.home.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.widyu.global.entity.Status;
import com.widyu.global.util.MemberUtil;
import com.widyu.goal.DailyGoalStatus;
import com.widyu.goal.healthschedule.repository.HealthScheduleRepository;
import com.widyu.goal.home.dto.response.GuardianGoalStatsResponse;
import com.widyu.goal.home.dto.response.SeniorWeeklyGoalStatusResponse;
import com.widyu.goal.medicineschedule.repository.MedicationProofRepository;
import com.widyu.goal.medicineschedule.repository.MedicineScheduleRepository;
import com.widyu.goal.walk.repository.WalkRepository;
import com.widyu.member.Member;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import com.widyu.medicine.MedicationProof;
import com.widyu.medicine.MedicineSchedule;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GoalHomeService 주간 통계 단위 테스트")
class GoalHomeServiceTest {

    @Mock private FamilyMembershipRepository familyMembershipRepository;
    @Mock private SeniorProfileRepository seniorProfileRepository;
    @Mock private MedicineScheduleRepository medicineScheduleRepository;
    @Mock private MedicationProofRepository medicationProofRepository;
    @Mock private WalkRepository walkRepository;
    @Mock private HealthScheduleRepository healthScheduleRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private MemberUtil memberUtil;

    @InjectMocks private GoalHomeService goalHomeService;

    @Test
    @DisplayName("시니어 주간 상태는 기간 데이터를 한 번씩 조회하고 기존 날짜별 상태를 유지한다")
    void 시니어_주간_상태는_기간_데이터를_한_번씩_조회한다() {
        // given
        LocalDate today = LocalDate.now();
        LocalDate startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate endOfWeek = startOfWeek.plusDays(6);
        Member member = mock(Member.class);
        MedicineSchedule schedule = mock(MedicineSchedule.class);
        MedicationProof proof = mock(MedicationProof.class);

        given(memberUtil.getCurrentMember()).willReturn(member);
        given(member.getId()).willReturn(11L);
        given(schedule.getId()).willReturn(1L);
        given(schedule.isEffectiveOn(any(LocalDate.class))).willReturn(true);
        given(proof.getMedicineSchedule()).willReturn(schedule);
        given(proof.getVerifiedAt()).willReturn(today.atTime(12, 0));
        given(medicineScheduleRepository.findEffectiveByMemberAndDateRange(
                member, Status.ACTIVE, startOfWeek, endOfWeek)).willReturn(List.of(schedule));
        given(medicationProofRepository.findByMemberIdAndDateRange(
                11L, startOfWeek.atStartOfDay(), endOfWeek.atTime(java.time.LocalTime.MAX)))
                .willReturn(List.of(proof));
        given(walkRepository.findByMemberAndWalkDateBetweenOrderByWalkDateAsc(
                member, startOfWeek, endOfWeek)).willReturn(List.of());

        // when
        SeniorWeeklyGoalStatusResponse response = goalHomeService.getSeniorWeeklyGoalStatus();

        // then
        List<DailyGoalStatus> expected = new ArrayList<>();
        for (LocalDate date = startOfWeek; !date.isAfter(endOfWeek); date = date.plusDays(1)) {
            if (date.isBefore(today)) {
                expected.add(DailyGoalStatus.FAILED);
            } else if (date.isEqual(today)) {
                expected.add(DailyGoalStatus.COMPLETED);
            } else {
                expected.add(DailyGoalStatus.NOT_STARTED);
            }
        }
        assertThat(response.thisWeekGoalRates()).containsExactlyElementsOf(expected);
        verify(medicineScheduleRepository, times(1))
                .findEffectiveByMemberAndDateRange(member, Status.ACTIVE, startOfWeek, endOfWeek);
        verify(medicationProofRepository, times(1))
                .findByMemberIdAndDateRange(11L, startOfWeek.atStartOfDay(), endOfWeek.atTime(java.time.LocalTime.MAX));
        verify(walkRepository, times(1))
                .findByMemberAndWalkDateBetweenOrderByWalkDateAsc(member, startOfWeek, endOfWeek);
    }

    @Test
    @DisplayName("보호자 주간 통계는 14일 데이터를 한 번씩 조회하고 이번 주 일별 결과를 재사용한다")
    void 보호자_주간_통계는_기간_데이터를_한_번씩_조회한다() {
        // given
        LocalDate today = LocalDate.now();
        LocalDate thisWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate thisWeekEnd = thisWeekStart.plusDays(6);
        LocalDate lastWeekStart = thisWeekStart.minusWeeks(1);
        Member member = mock(Member.class);
        MedicineSchedule schedule = mock(MedicineSchedule.class);
        MedicationProof proof = mock(MedicationProof.class);

        given(memberRepository.findById(11L)).willReturn(Optional.of(member));
        given(member.getId()).willReturn(11L);
        given(schedule.getId()).willReturn(1L);
        given(schedule.isEffectiveOn(any(LocalDate.class))).willReturn(true);
        given(proof.getMedicineSchedule()).willReturn(schedule);
        given(proof.getVerifiedAt()).willReturn(LocalDateTime.of(today, java.time.LocalTime.NOON));
        given(medicineScheduleRepository.findEffectiveByMemberAndDateRange(
                member, Status.ACTIVE, lastWeekStart, thisWeekEnd)).willReturn(List.of(schedule));
        given(medicationProofRepository.findByMemberIdAndDateRange(
                11L, lastWeekStart.atStartOfDay(), thisWeekEnd.atTime(java.time.LocalTime.MAX)))
                .willReturn(List.of(proof));
        given(walkRepository.findByMemberAndWalkDateBetweenOrderByWalkDateAsc(
                member, lastWeekStart, thisWeekEnd)).willReturn(List.of());

        // when
        GuardianGoalStatsResponse response = goalHomeService.getGuardianGoalStats(11L);

        // then
        long elapsedDays = ChronoUnit.DAYS.between(thisWeekStart, today) + 1;
        List<Double> expectedDailyRates = new ArrayList<>();
        for (LocalDate date = thisWeekStart; !date.isAfter(thisWeekEnd); date = date.plusDays(1)) {
            if (date.isEqual(today)) {
                expectedDailyRates.add(1.0);
            } else {
                expectedDailyRates.add(0.0);
            }
        }
        assertThat(response.lastWeekGoalRate()).isZero();
        assertThat(response.thisWeekGoalRate()).isEqualTo(1.0 / elapsedDays);
        assertThat(response.thisWeekGoalRates()).containsExactlyElementsOf(expectedDailyRates);
        verify(medicineScheduleRepository, times(1))
                .findEffectiveByMemberAndDateRange(member, Status.ACTIVE, lastWeekStart, thisWeekEnd);
        verify(medicationProofRepository, times(1)).findByMemberIdAndDateRange(
                11L, lastWeekStart.atStartOfDay(), thisWeekEnd.atTime(java.time.LocalTime.MAX));
        verify(walkRepository, times(1))
                .findByMemberAndWalkDateBetweenOrderByWalkDateAsc(member, lastWeekStart, thisWeekEnd);
    }
}
