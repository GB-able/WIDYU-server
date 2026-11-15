package com.widyu.home.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

import com.widyu.global.entity.Status;
import com.widyu.global.util.MemberUtil;
import com.widyu.goal.healthschedule.repository.HealthScheduleRepository;
import com.widyu.goal.medicineschedule.application.TodayMedicationQuery;
import com.widyu.goal.medicineschedule.repository.MedicationProofRepository;
import com.widyu.goal.medicineschedule.repository.MedicineScheduleRepository;
import com.widyu.goal.walk.repository.WalkRepository;
import com.widyu.heart.repository.HeartRateResultRepository;
import com.widyu.home.application.GuardianHomeService.GuardianHomeCards;
import com.widyu.location.realtime.application.LocationQueryService;
import com.widyu.member.Family;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("GuardianHomeService 홈 카드 열람 대상 단위 테스트")
class GuardianHomeServiceTest {

    private static final Long GUARDIAN_ID = 1L;
    private static final Long SENIOR_ID = 2L;

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
    @Mock private LocationQueryService locationQueryService;

    private GuardianHomeService guardianHomeService() {
        return new GuardianHomeService(memberUtil, memberRepository, familyMembershipRepository, seniorProfileRepository,
                heartRateResultRepository, new TodayMedicationQuery(medicineScheduleRepository, medicationProofRepository),
                healthScheduleRepository, walkRepository, albumRecommendationService, locationQueryService);
    }

    @Test
    @DisplayName("보호자가 홈 카드를 열면 외출 상태를 읽은 보호자와 시니어 식별자를 카드와 함께 반환한다")
    void 홈_카드와_함께_열람_기록에_쓸_보호자와_시니어를_반환한다() {
        // given
        givenEmptyHomeCards();

        // when
        GuardianHomeCards cards = guardianHomeService().getHomeCards(SENIOR_ID);

        // then
        assertThat(cards.guardianId()).isEqualTo(GUARDIAN_ID);
        assertThat(cards.seniorId()).isEqualTo(SENIOR_ID);
        assertThat(cards.response().isOuting()).isTrue();
    }

    private void givenEmptyHomeCards() {
        Member guardian = member(GUARDIAN_ID, MemberType.GUARDIAN, "김보호");
        Member senior = member(SENIOR_ID, MemberType.SENIOR, "부모님");
        SeniorProfile seniorProfile = SeniorProfile.createSeniorProfile(
                senior, Family.createFamily("ABC123"), "서울시", "INV1234", LocalDate.of(1950, 1, 1));
        ReflectionTestUtils.setField(seniorProfile, "id", 10L);
        ReflectionTestUtils.setField(senior, "seniorProfile", seniorProfile);

        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(memberRepository.findById(SENIOR_ID)).willReturn(Optional.of(senior));
        given(seniorProfileRepository.findByMemberId(SENIOR_ID)).willReturn(Optional.of(seniorProfile));
        given(familyMembershipRepository.existsByFamilyIdAndGuardianId(any(), anyLong())).willReturn(true);
        given(locationQueryService.getOutingStatus(SENIOR_ID)).willReturn(Boolean.TRUE);
        given(heartRateResultRepository.findByMemberId(SENIOR_ID)).willReturn(Optional.empty());
        given(medicineScheduleRepository.findEffectiveByMemberAndDateWithDetails(
                any(Member.class), any(Status.class), any(LocalDate.class))).willReturn(List.of());
        given(albumRecommendationService.findLatestAlbums(any(Member.class)))
                .willReturn(List.of());
        given(healthScheduleRepository.findFirstByMemberIdAndScheduledAtAfterOrderByScheduledAtAsc(
                anyLong(), any(LocalDateTime.class))).willReturn(Optional.empty());
        given(walkRepository.findByMemberAndWalkDate(any(Member.class), any(LocalDate.class)))
                .willReturn(Optional.empty());
    }

    private Member member(Long id, MemberType type, String name) {
        Member member = Member.createMember(type, name, "01011112222");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
