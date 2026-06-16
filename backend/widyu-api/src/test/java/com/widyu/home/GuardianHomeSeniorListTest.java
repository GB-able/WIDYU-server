package com.widyu.home;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.widyu.album.repository.AlbumRepository;
import com.widyu.global.error.BusinessException;
import com.widyu.global.util.MemberUtil;
import com.widyu.goal.healthschedule.repository.HealthScheduleRepository;
import com.widyu.goal.medicineschedule.repository.MedicationProofRepository;
import com.widyu.goal.medicineschedule.repository.MedicineScheduleRepository;
import com.widyu.goal.walk.repository.WalkRepository;
import com.widyu.heart.repository.HeartRateResultRepository;
import com.widyu.home.application.GuardianHomeService;
import com.widyu.home.dto.response.GuardianSeniorListResponse;
import com.widyu.member.Family;
import com.widyu.member.FamilyMembership;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyMembershipRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("GuardianHomeService 시니어 목록 테스트")
class GuardianHomeSeniorListTest {

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
    @Mock private AlbumRepository albumRepository;

    @Test
    @DisplayName("보호자가 가족 시니어 목록을 조회하면 memberId, 이름, 프로필 이미지를 반환한다")
    void 보호자_가족_시니어_목록_조회() {
        Member guardian = mock(Member.class);
        Family family = mock(Family.class);
        FamilyMembership membership = mock(FamilyMembership.class);
        SeniorProfile profile1 = mock(SeniorProfile.class);
        SeniorProfile profile2 = mock(SeniorProfile.class);
        Member senior1 = mock(Member.class);
        Member senior2 = mock(Member.class);

        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(guardian.getType()).willReturn(MemberType.GUARDIAN);
        given(guardian.getId()).willReturn(1L);
        given(familyMembershipRepository.findByGuardianId(1L)).willReturn(Optional.of(membership));
        given(membership.getFamily()).willReturn(family);
        given(family.getId()).willReturn(10L);
        given(seniorProfileRepository.findAllByFamilyIdWithMember(10L))
                .willReturn(List.of(profile1, profile2));

        given(profile1.getMember()).willReturn(senior1);
        given(senior1.getId()).willReturn(101L);
        given(senior1.getName()).willReturn("김영희");
        given(senior1.getProfileImage()).willReturn("senior1.png");

        given(profile2.getMember()).willReturn(senior2);
        given(senior2.getId()).willReturn(102L);
        given(senior2.getName()).willReturn("박철수");
        given(senior2.getProfileImage()).willReturn("senior2.png");

        GuardianSeniorListResponse response = guardianHomeService.getFamilySeniors();

        assertThat(response.seniors()).hasSize(2);
        assertThat(response.seniors().get(0).memberId()).isEqualTo(101L);
        assertThat(response.seniors().get(0).name()).isEqualTo("김영희");
        assertThat(response.seniors().get(0).profileImage()).isEqualTo("senior1.png");
        assertThat(response.seniors().get(1).memberId()).isEqualTo(102L);
    }

    @Test
    @DisplayName("연결된 가족이 없으면 빈 시니어 목록을 반환한다")
    void 연결된_가족이_없으면_빈_목록() {
        Member guardian = mock(Member.class);

        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(guardian.getType()).willReturn(MemberType.GUARDIAN);
        given(guardian.getId()).willReturn(1L);
        given(familyMembershipRepository.findByGuardianId(1L)).willReturn(Optional.empty());

        GuardianSeniorListResponse response = guardianHomeService.getFamilySeniors();

        assertThat(response.seniors()).isEmpty();
    }

    @Test
    @DisplayName("시니어가 가족 시니어 목록을 조회하면 403 예외가 발생한다")
    void 시니어가_목록_조회하면_403() {
        Member senior = mock(Member.class);

        given(memberUtil.getCurrentMember()).willReturn(senior);
        given(senior.getType()).willReturn(MemberType.SENIOR);

        assertThatThrownBy(() -> guardianHomeService.getFamilySeniors())
                .isInstanceOf(BusinessException.class);
    }
}
