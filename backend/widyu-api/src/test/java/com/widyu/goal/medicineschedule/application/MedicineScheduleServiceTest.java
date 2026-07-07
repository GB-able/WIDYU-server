package com.widyu.goal.medicineschedule.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.widyu.global.entity.Status;
import com.widyu.global.util.MemberUtil;
import com.widyu.goal.medicineschedule.dto.response.MedicationStatus;
import com.widyu.goal.medicineschedule.dto.response.MedicineScheduleDailyResponse;
import com.widyu.goal.medicineschedule.dto.response.MedicineScheduleDailyResponse.ScheduleItem;
import com.widyu.goal.medicineschedule.repository.MedicationProofRepository;
import com.widyu.goal.medicineschedule.repository.MedicineRepository;
import com.widyu.goal.medicineschedule.repository.MedicineScheduleRepository;
import com.widyu.medicine.MedicationProof;
import com.widyu.medicine.MedicineSchedule;
import com.widyu.member.Member;
import com.widyu.member.repository.MemberRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("MedicineScheduleService 일자별 조회 단위 테스트")
class MedicineScheduleServiceTest {

    @Mock private MedicineScheduleRepository medicineScheduleRepository;
    @Mock private MedicineRepository medicineRepository;
    @Mock private MedicationProofRepository medicationProofRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private MemberUtil memberUtil;

    @InjectMocks private MedicineScheduleService medicineScheduleService;

    private MedicineSchedule scheduleWithId(Long id, LocalTime alarmTime) {
        Member member = mock(Member.class);
        MedicineSchedule schedule = MedicineSchedule.create(member, alarmTime);
        ReflectionTestUtils.setField(schedule, "id", id);
        return schedule;
    }

    @Test
    @DisplayName("지난 날짜를 조회하면 인증 스케줄은 DONE과 인증 이미지, 미인증 스케줄은 MISSED와 null을 반환한다")
    void 지난_날짜_조회하면_인증_스케줄은_DONE과_이미지_미인증은_MISSED와_null을_반영한다() {
        // given
        Long memberId = 1L;
        LocalDate pastDate = LocalDate.of(2020, 1, 1);
        Member targetMember = mock(Member.class);

        MedicineSchedule verified = scheduleWithId(10L, LocalTime.of(8, 0));
        MedicineSchedule notVerified = scheduleWithId(20L, LocalTime.of(20, 0));

        MedicationProof proof = mock(MedicationProof.class);
        given(proof.getMedicineSchedule()).willReturn(verified);
        given(proof.getProofImageUrls()).willReturn(List.of("https://widyu.shop/proof/10.jpg"));

        given(memberRepository.findById(memberId)).willReturn(Optional.of(targetMember));
        given(targetMember.getId()).willReturn(memberId);
        given(medicineScheduleRepository.findByMemberAndStatusWithDetails(targetMember, Status.ACTIVE))
                .willReturn(List.of(verified, notVerified));
        given(medicationProofRepository.findByMemberIdAndDateRange(anyLong(), any(), any()))
                .willReturn(List.of(proof));

        // when
        MedicineScheduleDailyResponse response = medicineScheduleService.getDailySchedules(memberId, pastDate);

        // then
        Map<Long, ScheduleItem> itemByScheduleId = response.medicineSchedules().stream()
                .collect(Collectors.toMap(ScheduleItem::medicineScheduleId, item -> item));
        assertThat(itemByScheduleId.get(10L).status()).isEqualTo(MedicationStatus.DONE);
        assertThat(itemByScheduleId.get(10L).proofImageUrl()).isEqualTo("https://widyu.shop/proof/10.jpg");
        assertThat(itemByScheduleId.get(20L).status()).isEqualTo(MedicationStatus.MISSED);
        assertThat(itemByScheduleId.get(20L).proofImageUrl()).isNull();
    }

    @Test
    @DisplayName("활성 스케줄이 없으면 빈 목록을 반환하고 복용 인증 조회를 하지 않는다")
    void 활성_스케줄이_없으면_빈_목록을_반환하고_인증조회를_생략한다() {
        // given
        Long memberId = 1L;
        LocalDate date = LocalDate.of(2026, 7, 6);
        Member targetMember = mock(Member.class);

        given(memberRepository.findById(memberId)).willReturn(Optional.of(targetMember));
        given(medicineScheduleRepository.findByMemberAndStatusWithDetails(targetMember, Status.ACTIVE))
                .willReturn(List.of());

        // when
        MedicineScheduleDailyResponse response = medicineScheduleService.getDailySchedules(memberId, date);

        // then
        assertThat(response.medicineSchedules()).isEmpty();
        then(medicationProofRepository).should(never()).findByMemberIdAndDateRange(anyLong(), any(), any());
    }
}
