package com.widyu.home.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.widyu.album.application.AlbumPermissionService;
import com.widyu.global.entity.Status;
import com.widyu.global.util.MemberUtil;
import com.widyu.goal.healthschedule.repository.HealthScheduleRepository;
import com.widyu.goal.medicineschedule.application.TodayMedicationQuery;
import com.widyu.goal.medicineschedule.repository.MedicationProofRepository;
import com.widyu.goal.medicineschedule.repository.MedicineScheduleRepository;
import com.widyu.goal.walk.repository.WalkRepository;
import com.widyu.heart.repository.HeartRateResultRepository;
import com.widyu.home.dto.response.SeniorHomeCardsResponse;
import com.widyu.medicine.MedicationProof;
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
    @Mock private AlbumPermissionService albumPermissionService;

    private SeniorHomeService seniorHomeService() {
        return new SeniorHomeService(memberUtil,
                new TodayMedicationQuery(medicineScheduleRepository, medicationProofRepository), walkRepository,
                healthScheduleRepository, heartRateResultRepository, albumRecommendationService, albumPermissionService);
    }

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
        SeniorHomeCardsResponse response = seniorHomeService().getHomeCards(LocalDateTime.of(TODAY, time));

        // then
        assertThat(response.medicine().medicineScheduleId()).isEqualTo(expectedScheduleId);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("홈 카드를 조회할 때 인증창이 겹친 앞 일정을 인증했으면 다음 일정을 반환한다")
    void 인증한_일정은_건너뛰고_다음_일정을_반환한다() {
        // given
        Member senior = senior();
        MedicineSchedule verified = alarmSchedule(senior, 69L, LocalTime.of(17, 13));
        given(memberUtil.getCurrentMember()).willReturn(senior);
        given(medicineScheduleRepository.findEffectiveByMemberAndDateWithDetails(senior, Status.ACTIVE, TODAY))
                .willReturn(List.of(
                        verified,
                        alarmSchedule(senior, 70L, LocalTime.of(17, 15)),
                        alarmSchedule(senior, 71L, LocalTime.of(17, 20))));
        given(medicationProofRepository.findByMemberIdAndDateRange(any(), any(), any()))
                .willReturn(List.of(MedicationProof.create(verified, senior, List.of("proof.jpg"))));

        // when
        SeniorHomeCardsResponse response = seniorHomeService().getHomeCards(LocalDateTime.of(TODAY, LocalTime.of(17, 14)));

        // then
        assertThat(response.medicine().medicineScheduleId()).isEqualTo(70L);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("홈 카드를 조회할 때 같은 일정의 인증이 둘이면 가장 먼저 한 인증의 사진을 반환한다")
    void 중복_인증은_최초_인증을_대표로_반환한다() {
        // given: 조회 쿼리에 정렬이 없으므로 늦은 인증이 먼저 반환되는 순서를 재현한다.
        Member senior = senior();
        MedicineSchedule schedule = alarmSchedule(senior, 59L, LocalTime.of(21, 0));
        given(memberUtil.getCurrentMember()).willReturn(senior);
        given(medicineScheduleRepository.findEffectiveByMemberAndDateWithDetails(senior, Status.ACTIVE, TODAY))
                .willReturn(List.of(schedule));
        given(medicationProofRepository.findByMemberIdAndDateRange(any(), any(), any()))
                .willReturn(List.of(
                        proof(schedule, senior, TODAY.atTime(21, 25), "later.jpg"),
                        proof(schedule, senior, TODAY.atTime(21, 5), "earlier.jpg")));

        // when
        SeniorHomeCardsResponse response = seniorHomeService().getHomeCards(LocalDateTime.of(TODAY, LocalTime.of(21, 40)));

        // then
        assertThat(response.medicine().proofImageUrl()).isEqualTo("earlier.jpg");
        assertThat(response.medicine().todayTakenCount()).isEqualTo(1);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("홈 카드를 조회할 때 오늘 유효 목록에서 빠진 일정의 인증은 복용 횟수와 상태에 넣지 않는다")
    void 유효하지_않은_일정의_인증은_복용_횟수에서_제외한다() {
        // given: 오전에 인증한 뒤 일정을 수정해 옛 버전이 오늘 유효 목록에서 빠진 상황
        Member senior = senior();
        MedicineSchedule closedSchedule = alarmSchedule(senior, 59L, LocalTime.of(8, 0));
        given(memberUtil.getCurrentMember()).willReturn(senior);
        given(medicineScheduleRepository.findEffectiveByMemberAndDateWithDetails(senior, Status.ACTIVE, TODAY))
                .willReturn(List.of(alarmSchedule(senior, 60L, LocalTime.of(9, 0))));
        given(medicationProofRepository.findByMemberIdAndDateRange(any(), any(), any()))
                .willReturn(List.of(proof(closedSchedule, senior, TODAY.atTime(8, 5), "old.jpg")));

        // when
        SeniorHomeCardsResponse response = seniorHomeService().getHomeCards(LocalDateTime.of(TODAY, LocalTime.of(9, 10)));

        // then
        assertThat(response.medicine().todayTakenCount()).isZero();
        assertThat(response.medicine().todayTotalCount()).isEqualTo(1);
        assertThat(response.medicine().scheduleStatuses().getFirst().taken()).isFalse();
        assertThat(response.medicine().proofImageUrl()).isNull();
    }

    private MedicationProof proof(MedicineSchedule schedule, Member member, LocalDateTime verifiedAt, String imageUrl) {
        MedicationProof proof = MedicationProof.create(schedule, member, List.of(imageUrl));
        ReflectionTestUtils.setField(proof, "verifiedAt", verifiedAt);
        return proof;
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
