package com.widyu.home.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.widyu.global.entity.Status;
import com.widyu.global.util.MemberUtil;
import com.widyu.goal.healthschedule.repository.HealthScheduleRepository;
import com.widyu.goal.medicineschedule.repository.MedicationProofRepository;
import com.widyu.goal.medicineschedule.repository.MedicineScheduleRepository;
import com.widyu.goal.walk.repository.WalkRepository;
import com.widyu.heart.repository.HeartRateResultRepository;
import com.widyu.home.dto.response.GuardianHomeCardsResponse;
import com.widyu.location.access.application.LocationAccessLogService;
import com.widyu.location.realtime.application.RealtimeLocationService;
import com.widyu.medicine.MedicationProof;
import com.widyu.medicine.MedicineSchedule;
import com.widyu.member.Family;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
@DisplayName("GuardianHomeService 복약 카드 단위 테스트")
class GuardianHomeServiceMedicineTest {

    private static final Long GUARDIAN_ID = 1L;
    private static final Long SENIOR_ID = 2L;
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 3);
    private static final LocalDateTime NOW = TODAY.atTime(21, 0);

    @Mock private MemberUtil memberUtil;
    @Mock private MemberRepository memberRepository;
    @Mock private FamilyMembershipRepository familyMembershipRepository;
    @Mock private SeniorProfileRepository seniorProfileRepository;
    @Mock private HeartRateResultRepository heartRateResultRepository;
    @Mock private MedicineScheduleRepository medicineScheduleRepository;
    @Mock private MedicationProofRepository medicationProofRepository;
    @Mock private HealthScheduleRepository healthScheduleRepository;
    @Mock private WalkRepository walkRepository;
    @Mock private HomeAlbumRecommendationService albumRecommendationService;
    @Mock private RealtimeLocationService realtimeLocationService;
    @Mock private LocationAccessLogService locationAccessLogService;

    @InjectMocks private GuardianHomeService guardianHomeService;

    @Test
    @DisplayName("서로 다른 일정 둘을 인증했으면 가장 최근 인증 사진과 일정별 복용 여부를 반환한다")
    void 가장_최근_인증_사진과_일정별_복용_여부를_반환한다() {
        // given
        Member senior = givenSenior();
        MedicineSchedule morning = alarmSchedule(senior, 1L, LocalTime.of(8, 0));
        MedicineSchedule noon = alarmSchedule(senior, 2L, LocalTime.of(12, 0));
        MedicineSchedule night = alarmSchedule(senior, 3L, LocalTime.of(20, 0));
        given(medicineScheduleRepository.findEffectiveByMemberAndDateWithDetails(senior, Status.ACTIVE, TODAY))
                .willReturn(List.of(morning, noon, night));
        given(medicationProofRepository.findByMemberIdAndDateRange(SENIOR_ID, TODAY, TODAY))
                .willReturn(List.of(
                        proof(noon, senior, TODAY.atTime(12, 10), "noon.jpg"),
                        proof(morning, senior, TODAY.atTime(8, 5), "morning.jpg")));

        // when
        GuardianHomeCardsResponse response = guardianHomeService.getHomeCards(SENIOR_ID, NOW);

        // then
        assertThat(response.medicine().totalCount()).isEqualTo(3);
        assertThat(response.medicine().latestProofImageUrl()).isEqualTo("noon.jpg");
        assertThat(response.medicine().scheduleStatuses())
                .extracting(GuardianHomeCardsResponse.ScheduleStatus::taken)
                .containsExactly(true, true, false);
    }

    private Member givenSenior() {
        Member guardian = member(GUARDIAN_ID, MemberType.GUARDIAN);
        Member senior = member(SENIOR_ID, MemberType.SENIOR);
        SeniorProfile seniorProfile = SeniorProfile.createSeniorProfile(
                senior, Family.createFamily("ABC123"), "서울시", "INV1234", LocalDate.of(1950, 1, 1));
        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(memberRepository.findById(SENIOR_ID)).willReturn(Optional.of(senior));
        given(seniorProfileRepository.findByMemberId(SENIOR_ID)).willReturn(Optional.of(seniorProfile));
        given(familyMembershipRepository.existsByFamilyIdAndGuardianId(seniorProfile.getFamily().getId(), GUARDIAN_ID))
                .willReturn(true);
        return senior;
    }

    private Member member(Long id, MemberType type) {
        Member member = Member.createMember(type, "회원", "01011112222");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private MedicineSchedule alarmSchedule(Member member, Long id, LocalTime alarmTime) {
        MedicineSchedule schedule = MedicineSchedule.create(member, alarmTime);
        ReflectionTestUtils.setField(schedule, "id", id);
        return schedule;
    }

    private MedicationProof proof(MedicineSchedule schedule, Member member, LocalDateTime verifiedAt, String imageUrl) {
        MedicationProof proof = MedicationProof.create(schedule, member, List.of(imageUrl));
        ReflectionTestUtils.setField(proof, "verifiedAt", verifiedAt);
        return proof;
    }
}
