package com.widyu.goal.medicineschedule.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.widyu.global.entity.Status;
import com.widyu.goal.medicineschedule.repository.MedicationProofRepository;
import com.widyu.goal.medicineschedule.repository.MedicineScheduleRepository;
import com.widyu.member.Member;
import com.widyu.member.application.SeniorProfileService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MedicineScheduleRewardScheduler 단위 테스트")
class MedicineScheduleRewardSchedulerTest {

    @Mock private MedicationProofRepository medicationProofRepository;
    @Mock private MedicineScheduleRepository medicineScheduleRepository;
    @Mock private SeniorProfileService seniorProfileService;

    @InjectMocks private MedicineScheduleRewardScheduler scheduler;

    @Test
    @DisplayName("포인트 정산은 전날에 유효했던 스케줄 수를 기준으로 총 일정 수를 집계한다")
    void 정산은_전날_유효했던_스케줄_수를_기준으로_한다() {
        // given
        Member member = mock(Member.class);
        given(member.getId()).willReturn(1L);
        given(medicationProofRepository.findDistinctMembersByVerifiedAtBetween(any(), any()))
                .willReturn(List.of(member));
        given(medicationProofRepository.countByMemberAndVerifiedAtBetween(eq(member), any(), any()))
                .willReturn(2L);
        given(medicineScheduleRepository.countEffectiveByMemberAndDate(eq(member), eq(Status.ACTIVE), any()))
                .willReturn(2L);

        // when
        scheduler.settleDailyMedicationPoints();

        // then: 전날(어제) 유효했던 스케줄 수 기준으로 집계한다
        then(medicineScheduleRepository).should()
                .countEffectiveByMemberAndDate(eq(member), eq(Status.ACTIVE), eq(LocalDate.now().minusDays(1)));
        // 2회 인증 * 10p + 모든 일정 완료 보너스 20p
        then(seniorProfileService).should().addPointsToMember(1L, 40L);
    }
}
