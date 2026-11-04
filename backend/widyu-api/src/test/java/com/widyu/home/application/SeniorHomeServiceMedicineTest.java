package com.widyu.home.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.widyu.global.entity.Status;
import com.widyu.global.util.MemberUtil;
import com.widyu.goal.healthschedule.repository.HealthScheduleRepository;
import com.widyu.goal.medicineschedule.repository.MedicationProofRepository;
import com.widyu.goal.medicineschedule.repository.MedicineScheduleRepository;
import com.widyu.goal.walk.repository.WalkRepository;
import com.widyu.heart.repository.HeartRateResultRepository;
import com.widyu.home.dto.response.SeniorHomeCardsResponse;
import com.widyu.medicine.MedicineSchedule;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("SeniorHomeService 복약 카드 단위 테스트")
class SeniorHomeServiceMedicineTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);

    @Mock private MemberUtil memberUtil;
    @Mock private MedicineScheduleRepository medicineScheduleRepository;
    @Mock private MedicationProofRepository medicationProofRepository;
    @Mock private WalkRepository walkRepository;
    @Mock private HealthScheduleRepository healthScheduleRepository;
    @Mock private HeartRateResultRepository heartRateResultRepository;
    @Mock private HomeAlbumRecommendationService albumRecommendationService;

    @InjectMocks private SeniorHomeService seniorHomeService;

    @ParameterizedTest
    @CsvSource({"20:30, 59", "21:00:30, 59", "21:30, 59", "21:30:00.000000001, 60", "22:10, 60", "23:30, 60"})
    @DisplayName("홈 카드를 조회하면 알람 후 인증 마감 30분까지 그 일정을 유지하고 마감 뒤 다음 일정을 반환한다")
    void 인증_마감까지_현재_일정을_유지한다(LocalTime time, long expectedScheduleId) {
        // given
        Member senior = senior();
        given(memberUtil.getCurrentMember()).willReturn(senior);
        given(medicineScheduleRepository.findEffectiveByMemberAndDateWithDetails(senior, Status.ACTIVE, TODAY))
                .willReturn(List.of(
                        alarmSchedule(senior, 59L, LocalTime.of(21, 0)),
                        alarmSchedule(senior, 60L, LocalTime.of(22, 30))));
        given(medicationProofRepository.findByMemberIdAndDateRange(any(), any(), any())).willReturn(List.of());

        // when
        SeniorHomeCardsResponse response = seniorHomeService.getHomeCards(LocalDateTime.of(TODAY, time));

        // then
        assertThat(response.medicine().medicineScheduleId()).isEqualTo(expectedScheduleId);
    }

    private Member senior() {
        Member member = Member.createMember(MemberType.SENIOR, "부모님", "01011112222");
        ReflectionTestUtils.setField(member, "id", 28L);
        return member;
    }

    private MedicineSchedule alarmSchedule(Member member, Long id, LocalTime alarmTime) {
        MedicineSchedule schedule = MedicineSchedule.create(member, alarmTime);
        ReflectionTestUtils.setField(schedule, "id", id);
        return schedule;
    }
}
