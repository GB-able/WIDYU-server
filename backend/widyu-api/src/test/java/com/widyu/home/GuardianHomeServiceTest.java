package com.widyu.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.widyu.album.Album;
import com.widyu.global.entity.Status;
import com.widyu.global.error.BusinessException;
import com.widyu.global.util.MemberUtil;
import com.widyu.goal.healthschedule.repository.HealthScheduleRepository;
import com.widyu.goal.medicineschedule.repository.MedicationProofRepository;
import com.widyu.goal.medicineschedule.repository.MedicineScheduleRepository;
import com.widyu.goal.walk.repository.WalkRepository;
import com.widyu.heart.repository.HeartRateResultRepository;
import com.widyu.home.application.GuardianHomeService;
import com.widyu.home.application.HomeAlbumRecommendationService;
import com.widyu.home.dto.response.GuardianHomeCardsResponse;
import com.widyu.location.realtime.application.RealtimeLocationService;
import com.widyu.medicine.MedicineSchedule;
import com.widyu.member.Family;
import com.widyu.member.FamilyMembership;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GuardianHomeService 단위 테스트")
class GuardianHomeServiceTest {

    @InjectMocks private GuardianHomeService guardianHomeService;

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

    @Test
    @DisplayName("시니어가 보호자 홈을 호출하면 403 예외가 발생한다")
    void 시니어가_보호자홈_호출하면_403_예외가_발생한다() {
        // given
        Member senior = Member.createMember(MemberType.SENIOR, "부모님", "01011112222");
        given(memberUtil.getCurrentMember()).willReturn(senior);

        // when & then
        assertThatThrownBy(() -> guardianHomeService.getHomeCards(null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("연결되지 않은 시니어 ID를 요청하면 403 예외가 발생한다")
    void 연결되지_않은_시니어_ID_요청하면_403_예외가_발생한다() {
        // given
        Member guardian = Member.createMember(MemberType.GUARDIAN, "보호자", "01099999999");
        Member otherSenior = Member.createMember(MemberType.SENIOR, "다른가족부모님", "01055556666");
        Family otherFamily = Family.createFamily("OTHER1");
        SeniorProfile otherProfile = SeniorProfile.createSeniorProfile(
                otherSenior, otherFamily, "서울시", "9990001", null);

        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(memberRepository.findById(42L)).willReturn(Optional.of(otherSenior));
        given(seniorProfileRepository.findByMemberId(42L)).willReturn(Optional.of(otherProfile));
        given(familyMembershipRepository.existsByFamilyIdAndGuardianId(any(), any())).willReturn(false);

        // when & then
        assertThatThrownBy(() -> guardianHomeService.getHomeCards(42L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("약 스케줄이 없으면 medicine은 null을 반환한다")
    void 약_스케줄이_없으면_medicine은_null을_반환한다() {
        // given
        Member guardian = Member.createMember(MemberType.GUARDIAN, "보호자", "01099999999");
        Member senior = Member.createMember(MemberType.SENIOR, "부모님", "01011112222");
        Family family = Family.createFamily("FAMA01");
        FamilyMembership membership = FamilyMembership.createLeaderMembership(family, guardian);
        SeniorProfile seniorProfile = SeniorProfile.createSeniorProfile(
                senior, family, "서울시", "1110001", null);

        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(familyMembershipRepository.findByGuardianId(any())).willReturn(Optional.of(membership));
        given(seniorProfileRepository.findAllByFamilyIdWithMember(any())).willReturn(List.of(seniorProfile));
        given(heartRateResultRepository.findByMemberId(any())).willReturn(Optional.empty());
        given(medicineScheduleRepository.findEffectiveByMemberAndDateWithDetails(any(), any(), any())).willReturn(List.of());
        given(healthScheduleRepository.findByMemberIdAndDate(any(), any(), any())).willReturn(List.of());
        given(walkRepository.findByMemberAndWalkDate(any(), any())).willReturn(Optional.empty());
        given(albumRecommendationService.recommendAlbums(any(), any())).willReturn(List.of());
        given(realtimeLocationService.getOutingStatus(any())).willReturn(true);

        // when
        GuardianHomeCardsResponse response = guardianHomeService.getHomeCards(null);

        // then
        assertThat(response.medicine()).isNull();
        assertThat(response.isOuting()).isTrue();
    }

    @Test
    @DisplayName("오늘 건강 일정이 없으면 healthSchedule은 null을 반환한다")
    void 오늘_건강일정이_없으면_healthSchedule은_null을_반환한다() {
        // given
        Member guardian = Member.createMember(MemberType.GUARDIAN, "보호자", "01099999999");
        Member senior = Member.createMember(MemberType.SENIOR, "부모님", "01011112222");
        Family family = Family.createFamily("FAMA01");
        FamilyMembership membership = FamilyMembership.createLeaderMembership(family, guardian);
        SeniorProfile seniorProfile = SeniorProfile.createSeniorProfile(
                senior, family, "서울시", "1110001", null);

        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(familyMembershipRepository.findByGuardianId(any())).willReturn(Optional.of(membership));
        given(seniorProfileRepository.findAllByFamilyIdWithMember(any())).willReturn(List.of(seniorProfile));
        given(heartRateResultRepository.findByMemberId(any())).willReturn(Optional.empty());
        given(medicineScheduleRepository.findEffectiveByMemberAndDateWithDetails(any(), any(), any())).willReturn(List.of());
        given(healthScheduleRepository.findByMemberIdAndDate(any(), any(), any())).willReturn(List.of());
        given(walkRepository.findByMemberAndWalkDate(any(), any())).willReturn(Optional.empty());
        given(albumRecommendationService.recommendAlbums(any(), any())).willReturn(List.of());

        // when
        GuardianHomeCardsResponse response = guardianHomeService.getHomeCards(null);

        // then
        assertThat(response.healthSchedule()).isNull();
    }

    @Test
    @DisplayName("가족 앨범이 없으면 albums는 빈 배열을 반환한다")
    void 가족_앨범이_없으면_albums는_빈배열을_반환한다() {
        // given
        Member guardian = Member.createMember(MemberType.GUARDIAN, "보호자", "01099999999");
        Member senior = Member.createMember(MemberType.SENIOR, "부모님", "01011112222");
        Family family = Family.createFamily("FAMA01");
        FamilyMembership membership = FamilyMembership.createLeaderMembership(family, guardian);
        SeniorProfile seniorProfile = SeniorProfile.createSeniorProfile(
                senior, family, "서울시", "1110001", null);

        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(familyMembershipRepository.findByGuardianId(any())).willReturn(Optional.of(membership));
        given(seniorProfileRepository.findAllByFamilyIdWithMember(any())).willReturn(List.of(seniorProfile));
        given(heartRateResultRepository.findByMemberId(any())).willReturn(Optional.empty());
        given(medicineScheduleRepository.findEffectiveByMemberAndDateWithDetails(any(), any(), any())).willReturn(List.of());
        given(healthScheduleRepository.findByMemberIdAndDate(any(), any(), any())).willReturn(List.of());
        given(walkRepository.findByMemberAndWalkDate(any(), any())).willReturn(Optional.empty());
        given(albumRecommendationService.recommendAlbums(any(), any())).willReturn(List.of());

        // when
        GuardianHomeCardsResponse response = guardianHomeService.getHomeCards(null);

        // then
        assertThat(response.albums()).isEmpty();
    }

    @Test
    @DisplayName("약 스케줄의 totalCount는 스케줄 횟수를 반환한다")
    void 약_스케줄의_totalCount는_스케줄_횟수를_반환한다() {
        // given
        Member guardian = Member.createMember(MemberType.GUARDIAN, "보호자", "01099999999");
        Member senior = Member.createMember(MemberType.SENIOR, "부모님", "01011112222");
        Family family = Family.createFamily("FAMA01");
        FamilyMembership membership = FamilyMembership.createLeaderMembership(family, guardian);
        SeniorProfile seniorProfile = SeniorProfile.createSeniorProfile(
                senior, family, "서울시", "1110001", null);

        MedicineSchedule schedule1 = mock(MedicineSchedule.class);
        MedicineSchedule schedule2 = mock(MedicineSchedule.class);
        given(schedule1.getId()).willReturn(1L);
        given(schedule1.getAlarmTime()).willReturn(LocalTime.of(8, 0));
        given(schedule2.getId()).willReturn(2L);
        given(schedule2.getAlarmTime()).willReturn(LocalTime.of(20, 0));

        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(familyMembershipRepository.findByGuardianId(any())).willReturn(Optional.of(membership));
        given(seniorProfileRepository.findAllByFamilyIdWithMember(any())).willReturn(List.of(seniorProfile));
        given(heartRateResultRepository.findByMemberId(any())).willReturn(Optional.empty());
        given(medicineScheduleRepository.findEffectiveByMemberAndDateWithDetails(any(), any(), any()))
                .willReturn(List.of(schedule1, schedule2));
        given(medicationProofRepository.findByMemberIdAndDateRange(any(), any(), any()))
                .willReturn(List.of());
        given(healthScheduleRepository.findByMemberIdAndDate(any(), any(), any())).willReturn(List.of());
        given(walkRepository.findByMemberAndWalkDate(any(), any())).willReturn(Optional.empty());
        given(albumRecommendationService.recommendAlbums(any(), any())).willReturn(List.of());

        // when
        GuardianHomeCardsResponse response = guardianHomeService.getHomeCards(null);

        // then: 스케줄 개수 기준 (알 개수 합계 5가 아닌 스케줄 횟수 2)
        assertThat(response.medicine().totalCount()).isEqualTo(2);
    }
}
