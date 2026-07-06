package com.widyu.goal.medicineschedule.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.widyu.global.entity.Status;
import com.widyu.global.util.MemberUtil;
import com.widyu.goal.medicineschedule.dto.response.MedicineScheduleDailyResponse;
import com.widyu.goal.medicineschedule.dto.response.MedicineScheduleDailyResponse.ScheduleItem;
import com.widyu.goal.medicineschedule.repository.MedicationProofRepository;
import com.widyu.goal.medicineschedule.repository.MedicineRepository;
import com.widyu.goal.medicineschedule.repository.MedicineScheduleRepository;
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
    @DisplayName("선택한 날짜에 인증된 스케줄은 taken이 true로, 인증되지 않은 스케줄은 false로 반환된다")
    void 일자별_조회하면_스케줄별_복용_인증_여부가_taken으로_반영된다() {
        // given
        Long memberId = 1L;
        LocalDate date = LocalDate.of(2026, 7, 6);
        Member targetMember = mock(Member.class);

        MedicineSchedule verified = scheduleWithId(10L, LocalTime.of(8, 0));
        MedicineSchedule notVerified = scheduleWithId(20L, LocalTime.of(20, 0));

        given(memberRepository.findById(memberId)).willReturn(Optional.of(targetMember));
        given(medicineScheduleRepository.findByMemberAndStatusWithDetails(targetMember, Status.ACTIVE))
                .willReturn(List.of(verified, notVerified));
        given(medicationProofRepository.findVerifiedScheduleIds(anyList(), any(), any()))
                .willReturn(List.of(10L));

        // when
        MedicineScheduleDailyResponse response = medicineScheduleService.getDailySchedules(memberId, date);

        // then
        Map<Long, Boolean> takenByScheduleId = response.medicineSchedule().stream()
                .collect(Collectors.toMap(ScheduleItem::medicineScheduleId, ScheduleItem::taken));
        assertThat(takenByScheduleId.get(10L)).isTrue();
        assertThat(takenByScheduleId.get(20L)).isFalse();
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
        assertThat(response.medicineSchedule()).isEmpty();
        then(medicationProofRepository).should(never()).findVerifiedScheduleIds(anyList(), any(), any());
    }
}
