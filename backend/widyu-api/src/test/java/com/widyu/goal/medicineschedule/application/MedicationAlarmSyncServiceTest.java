package com.widyu.goal.medicineschedule.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.widyu.global.entity.Status;
import com.widyu.global.util.MemberUtil;
import com.widyu.goal.medicineschedule.dto.response.MedicationAlarmSyncResponse;
import com.widyu.goal.medicineschedule.repository.MedicationProofRepository;
import com.widyu.goal.medicineschedule.repository.MedicineScheduleRepository;
import com.widyu.member.Member;
import com.widyu.medicine.Medicine;
import com.widyu.medicine.MedicineCategory;
import com.widyu.medicine.MedicineSchedule;
import com.widyu.medicine.MedicineScheduleDetail;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("MedicationAlarmSyncService 단위 테스트")
class MedicationAlarmSyncServiceTest {

    @Mock private MemberUtil memberUtil;
    @Mock private MedicineScheduleRepository medicineScheduleRepository;
    @Mock private MedicationProofRepository medicationProofRepository;

    @InjectMocks private MedicationAlarmSyncService medicationAlarmSyncService;

    @Test
    @DisplayName("스냅샷을 조회하면 오늘 이후 유효한 스케줄을 유효 기간과 함께 revision 및 Asia/Seoul 기준으로 반환한다")
    void 스냅샷을_조회하면_오늘_이후_유효한_스케줄을_유효_기간과_함께_반환한다() {
        // given: 오늘까지만 유효한 08시와 내일 시작하는 20시
        Member member = org.mockito.Mockito.mock(Member.class);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        MedicineSchedule endsToday = schedule(member, 15L, LocalTime.of(8, 0), 1, today.minusDays(5));
        endsToday.closeAsOf(today);
        MedicineSchedule startsTomorrow = schedule(member, 21L, LocalTime.of(20, 0), 2, today.plusDays(1));

        given(memberUtil.getCurrentMember()).willReturn(member);
        given(member.getId()).willReturn(1L);
        given(member.getMedicationAlarmRevision()).willReturn(42L);
        given(medicineScheduleRepository.findEffectiveFromDateByMemberWithDetails(member, Status.ACTIVE, today))
                .willReturn(List.of(endsToday, startsTomorrow));
        given(medicationProofRepository.findVerifiedScheduleIdsByMemberAndDate(1L, today))
                .willReturn(List.of(15L));

        // when
        MedicationAlarmSyncResponse response = medicationAlarmSyncService.getCurrentMemberSnapshot();

        // then
        assertThat(response.revision()).isEqualTo(42L);
        assertThat(response.timeZone()).isEqualTo("Asia/Seoul");
        assertThat(response.schedules()).containsExactly(
                new MedicationAlarmSyncResponse.Schedule(15L, "08:00", 1, today.minusDays(5), today),
                new MedicationAlarmSyncResponse.Schedule(21L, "20:00", 2, today.plusDays(1), null));
        assertThat(response.completed()).containsExactly("15:" + today);
    }

    private static MedicineSchedule schedule(Member member, Long id, LocalTime alarmTime, int doseCount,
                                             LocalDate effectiveFrom) {
        MedicineSchedule schedule = MedicineSchedule.create(member, alarmTime, effectiveFrom);
        MedicineCategory category = MedicineCategory.create("약");
        category.addMedicine(MedicineScheduleDetail.create(org.mockito.Mockito.mock(Medicine.class), doseCount));
        schedule.addCategory(category);
        ReflectionTestUtils.setField(schedule, "id", id);
        return schedule;
    }
}
