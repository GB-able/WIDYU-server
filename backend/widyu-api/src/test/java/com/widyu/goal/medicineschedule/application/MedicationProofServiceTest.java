package com.widyu.goal.medicineschedule.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

import com.widyu.global.entity.Status;
import com.widyu.global.error.BusinessException;
import com.widyu.global.infrastructure.s3.S3Service;
import com.widyu.global.util.MemberUtil;
import com.widyu.goal.medicineschedule.repository.MedicationProofRepository;
import com.widyu.goal.medicineschedule.repository.MedicineScheduleRepository;
import com.widyu.member.Member;
import com.widyu.medicine.MedicationProof;
import com.widyu.medicine.MedicineSchedule;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("MedicationProofService 복용 인증 단위 테스트")
class MedicationProofServiceTest {

    @Mock private MedicationProofRepository medicationProofRepository;
    @Mock private MedicineScheduleRepository medicineScheduleRepository;
    @Mock private MemberUtil memberUtil;
    @Mock private S3Service s3Service;

    @InjectMocks private MedicationProofService medicationProofService;

    @Test
    @DisplayName("오늘 유효하지 않은 과거 스케줄로 복용 인증하면 예외가 발생한다")
    void 오늘_유효하지_않은_과거_스케줄은_복용_인증할_수_없다() {
        // given
        Long scheduleId = 1L;
        Member member = org.mockito.Mockito.mock(Member.class);
        given(member.getId()).willReturn(10L);
        given(memberUtil.getCurrentMember()).willReturn(member);

        MedicineSchedule closedSchedule = MedicineSchedule.create(member, LocalTime.now());
        ReflectionTestUtils.setField(closedSchedule, "effectiveFrom", LocalDate.now().minusDays(10));
        ReflectionTestUtils.setField(closedSchedule, "effectiveTo", LocalDate.now().minusDays(1));

        given(medicineScheduleRepository.findByIdAndStatusWithDetails(scheduleId, Status.ACTIVE))
                .willReturn(Optional.of(closedSchedule));

        // when & then
        assertThatThrownBy(() -> medicationProofService.verifyMedication(scheduleId, List.of()))
                .isInstanceOf(BusinessException.class);
        then(medicationProofRepository).should(never()).save(org.mockito.ArgumentMatchers.any(MedicationProof.class));
    }
}
