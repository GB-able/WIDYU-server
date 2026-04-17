package com.widyu.mypage.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.widyu.global.error.BusinessException;
import com.widyu.global.infrastructure.s3.S3Service;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.FamilyConnection;
import com.widyu.member.LocalAccount;
import com.widyu.member.Member;
import com.widyu.member.SeniorProfile;
import com.widyu.member.SocialAccount;
import com.widyu.member.repository.FamilyConnectionRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import com.widyu.mypage.dto.request.UpdateNameRequest;
import com.widyu.mypage.dto.request.UpdatePhoneRequest;
import com.widyu.mypage.dto.request.UpdateSeniorAddressRequest;
import com.widyu.mypage.dto.response.ConnectedSeniorResponse;
import com.widyu.mypage.dto.response.FamilyCodeResponse;
import com.widyu.mypage.dto.response.FamilyMemberListResponse;
import com.widyu.mypage.dto.response.GuardianInfoResponse;
import com.widyu.mypage.dto.response.GuardianProfileDetailResponse;
import com.widyu.mypage.dto.response.InviteCodeResponse;
import com.widyu.mypage.dto.response.SeniorProfileForGuardianResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class GuardianMyPageServiceTest {

    @Mock private MemberUtil memberUtil;
    @Mock private S3Service s3Service;
    @Mock private FamilyConnectionRepository familyConnectionRepository;
    @Mock private SeniorProfileRepository seniorProfileRepository;

    @InjectMocks
    private GuardianMyPageService guardianMyPageService;

    // ======================== 내 정보 조회 ========================

    @Test
    @DisplayName("보호자 내 정보를 조회하면 프로필 이미지와 이름을 반환한다")
    void 보호자_내정보_조회() {
        // given
        Member member = mock(Member.class);
        given(memberUtil.getCurrentMember()).willReturn(member);
        given(member.getProfileImage()).willReturn("image.png");
        given(member.getName()).willReturn("한토마");

        // when
        GuardianInfoResponse response = guardianMyPageService.getGuardianInfo();

        // then
        assertThat(response.name()).isEqualTo("한토마");
        assertThat(response.profileImage()).isEqualTo("image.png");
    }

    // ======================== 프로필 설정 조회 ========================

    @Test
    @DisplayName("로컬 계정으로 가입한 보호자의 프로필을 조회하면 이메일과 빈 소셜 목록을 반환한다")
    void 프로필_설정_조회_로컬계정() {
        // given
        Member member = mock(Member.class);
        LocalAccount localAccount = mock(LocalAccount.class);

        given(memberUtil.getCurrentMember()).willReturn(member);
        given(member.getName()).willReturn("한토마");
        given(member.getBirthDate()).willReturn("19900101");
        given(member.getPhoneNumber()).willReturn("01011112222");
        given(member.getProfileImage()).willReturn("image.png");
        given(member.getLocalAccount()).willReturn(localAccount);
        given(localAccount.getEmail()).willReturn("toma@daum.net");
        given(member.getSocialAccounts()).willReturn(List.of());

        // when
        GuardianProfileDetailResponse response = guardianMyPageService.getProfileDetail();

        // then
        assertThat(response.email()).isEqualTo("toma@daum.net");
        assertThat(response.socialProviders()).isEmpty();
    }

    @Test
    @DisplayName("소셜 계정으로 가입한 보호자의 프로필을 조회하면 소셜 이메일과 제공자 목록을 반환한다")
    void 프로필_설정_조회_소셜계정() {
        // given
        Member member = mock(Member.class);
        SocialAccount kakaoAccount = mock(SocialAccount.class);

        given(memberUtil.getCurrentMember()).willReturn(member);
        given(member.getName()).willReturn("한토마");
        given(member.getBirthDate()).willReturn(null);
        given(member.getPhoneNumber()).willReturn("01011112222");
        given(member.getProfileImage()).willReturn(null);
        given(member.getLocalAccount()).willReturn(null);
        given(member.getSocialAccounts()).willReturn(List.of(kakaoAccount));
        given(kakaoAccount.getEmail()).willReturn("toma@kakao.com");
        given(kakaoAccount.getProvider()).willReturn("kakao");

        // when
        GuardianProfileDetailResponse response = guardianMyPageService.getProfileDetail();

        // then
        assertThat(response.email()).isEqualTo("toma@kakao.com");
        assertThat(response.socialProviders()).containsExactly("kakao");
    }

    // ======================== 이름 수정 ========================

    @Test
    @DisplayName("이름을 수정하면 회원 엔티티의 이름이 변경된다")
    void 이름_수정() {
        // given
        Member member = mock(Member.class);
        given(memberUtil.getCurrentMember()).willReturn(member);

        // when
        guardianMyPageService.updateName(new UpdateNameRequest("새이름"));

        // then
        verify(member).updateName("새이름");
    }

    // ======================== 프로필 이미지 수정 ========================

    @Test
    @DisplayName("기존 프로필 이미지가 있을 때 새 이미지로 교체하면 기존 이미지가 S3에서 삭제된다")
    void 프로필_이미지_수정_기존이미지_삭제() {
        // given
        Member member = mock(Member.class);
        given(memberUtil.getCurrentMember()).willReturn(member);
        given(member.getProfileImage()).willReturn("https://s3.old.png");
        given(s3Service.generateFilePath(anyString(), anyString())).willReturn("profile/new.png");
        given(s3Service.uploadFile(any(), anyString())).willReturn("https://s3.new.png");

        MockMultipartFile image = new MockMultipartFile("image", "new.png", "image/png", new byte[]{1});

        // when
        guardianMyPageService.updateProfileImage(image);

        // then
        verify(s3Service).deleteFile("https://s3.old.png");
        verify(member).updateProfileImage("https://s3.new.png");
    }

    @Test
    @DisplayName("기존 프로필 이미지가 없을 때 새 이미지를 등록하면 S3 삭제 없이 업로드만 진행된다")
    void 프로필_이미지_수정_기존이미지_없음() {
        // given
        Member member = mock(Member.class);
        given(memberUtil.getCurrentMember()).willReturn(member);
        given(member.getProfileImage()).willReturn(null);
        given(s3Service.generateFilePath(anyString(), anyString())).willReturn("profile/new.png");
        given(s3Service.uploadFile(any(), anyString())).willReturn("https://s3.new.png");

        MockMultipartFile image = new MockMultipartFile("image", "new.png", "image/png", new byte[]{1});

        // when
        guardianMyPageService.updateProfileImage(image);

        // then
        verify(s3Service, never()).deleteFile(anyString());
        verify(member).updateProfileImage("https://s3.new.png");
    }

    // ======================== 연결된 시니어 목록 조회 ========================

    @Test
    @DisplayName("연결된 시니어 목록을 조회하면 가족으로 연결된 시니어 정보 목록을 반환한다")
    void 연결된_시니어_목록_조회() {
        // given
        Member guardian = mock(Member.class);
        FamilyConnection connection1 = mock(FamilyConnection.class);
        FamilyConnection connection2 = mock(FamilyConnection.class);
        SeniorProfile profile1 = mock(SeniorProfile.class);
        SeniorProfile profile2 = mock(SeniorProfile.class);
        Member senior1 = mock(Member.class);
        Member senior2 = mock(Member.class);

        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(guardian.getId()).willReturn(1L);
        given(familyConnectionRepository.findAllByGuardianIdWithSeniorAndMember(1L))
                .willReturn(List.of(connection1, connection2));

        given(connection1.getSenior()).willReturn(profile1);
        given(profile1.getMember()).willReturn(senior1);
        given(senior1.getId()).willReturn(10L);
        given(senior1.getName()).willReturn("송애순");
        given(senior1.getProfileImage()).willReturn("img1.png");

        given(connection2.getSenior()).willReturn(profile2);
        given(profile2.getMember()).willReturn(senior2);
        given(senior2.getId()).willReturn(20L);
        given(senior2.getName()).willReturn("오일남");
        given(senior2.getProfileImage()).willReturn("img2.png");

        // when
        ConnectedSeniorResponse response = guardianMyPageService.getConnectedSeniors();

        // then
        assertThat(response.seniors()).hasSize(2);
        assertThat(response.seniors().get(0).name()).isEqualTo("송애순");
        assertThat(response.seniors().get(1).name()).isEqualTo("오일남");
    }

    // ======================== 시니어 프로필 조회 ========================

    @Test
    @DisplayName("연결된 시니어의 프로필을 조회하면 이름과 초대코드를 반환한다")
    void 시니어_프로필_조회() {
        // given
        Member guardian = mock(Member.class);
        SeniorProfile seniorProfile = mock(SeniorProfile.class);
        Member seniorMember = mock(Member.class);

        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(guardian.getId()).willReturn(1L);
        given(seniorProfileRepository.findByMemberId(10L)).willReturn(Optional.of(seniorProfile));
        given(seniorProfile.getId()).willReturn(100L);
        given(familyConnectionRepository.existsBySeniorIdAndGuardianId(100L, 1L)).willReturn(true);
        given(seniorProfile.getMember()).willReturn(seniorMember);
        given(seniorMember.getId()).willReturn(10L);
        given(seniorMember.getName()).willReturn("오일남");
        given(seniorProfile.getInviteCode()).willReturn("1234567");

        // when
        SeniorProfileForGuardianResponse response = guardianMyPageService.getSeniorProfile(10L);

        // then
        assertThat(response.name()).isEqualTo("오일남");
        assertThat(response.inviteCode()).isEqualTo("1234567");
    }

    @Test
    @DisplayName("가족으로 연결되지 않은 시니어의 프로필을 조회하면 예외가 발생한다")
    void 시니어_프로필_조회_접근권한_없음() {
        // given
        Member guardian = mock(Member.class);
        SeniorProfile seniorProfile = mock(SeniorProfile.class);

        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(guardian.getId()).willReturn(1L);
        given(seniorProfileRepository.findByMemberId(10L)).willReturn(Optional.of(seniorProfile));
        given(seniorProfile.getId()).willReturn(100L);
        given(familyConnectionRepository.existsBySeniorIdAndGuardianId(100L, 1L)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> guardianMyPageService.getSeniorProfile(10L))
                .isInstanceOf(BusinessException.class);
    }

    // ======================== 시니어 가족코드 조회 ========================

    @Test
    @DisplayName("연결된 시니어의 가족코드를 조회하면 6자리 코드를 반환한다")
    void 시니어_가족코드_조회() {
        // given
        Member guardian = mock(Member.class);
        SeniorProfile seniorProfile = mock(SeniorProfile.class);

        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(guardian.getId()).willReturn(1L);
        given(seniorProfileRepository.findByMemberId(10L)).willReturn(Optional.of(seniorProfile));
        given(seniorProfile.getId()).willReturn(100L);
        given(familyConnectionRepository.existsBySeniorIdAndGuardianId(100L, 1L)).willReturn(true);
        given(seniorProfile.getFamilyCode()).willReturn("AB12CD");

        // when
        FamilyCodeResponse response = guardianMyPageService.getFamilyCode(10L);

        // then
        assertThat(response.familyCode()).isEqualTo("AB12CD");
    }

    @Test
    @DisplayName("연결되지 않은 시니어의 가족코드를 조회하면 예외가 발생한다")
    void 시니어_가족코드_조회_접근권한_없음() {
        // given
        Member guardian = mock(Member.class);
        SeniorProfile seniorProfile = mock(SeniorProfile.class);

        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(guardian.getId()).willReturn(1L);
        given(seniorProfileRepository.findByMemberId(10L)).willReturn(Optional.of(seniorProfile));
        given(seniorProfile.getId()).willReturn(100L);
        given(familyConnectionRepository.existsBySeniorIdAndGuardianId(100L, 1L)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> guardianMyPageService.getFamilyCode(10L))
                .isInstanceOf(BusinessException.class);
    }

    // ======================== 시니어 초대코드 조회 ========================

    @Test
    @DisplayName("연결된 시니어의 초대코드를 조회하면 7자리 초대코드를 반환한다")
    void 시니어_초대코드_조회() {
        // given
        Member guardian = mock(Member.class);
        SeniorProfile seniorProfile = mock(SeniorProfile.class);

        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(guardian.getId()).willReturn(1L);
        given(seniorProfileRepository.findByMemberId(10L)).willReturn(Optional.of(seniorProfile));
        given(seniorProfile.getId()).willReturn(100L);
        given(familyConnectionRepository.existsBySeniorIdAndGuardianId(100L, 1L)).willReturn(true);
        given(seniorProfile.getInviteCode()).willReturn("1234567");

        // when
        InviteCodeResponse response = guardianMyPageService.getInviteCode(10L);

        // then
        assertThat(response.inviteCode()).isEqualTo("1234567");
    }

    @Test
    @DisplayName("연결되지 않은 시니어의 초대코드를 조회하면 예외가 발생한다")
    void 시니어_초대코드_조회_접근권한_없음() {
        // given
        Member guardian = mock(Member.class);
        SeniorProfile seniorProfile = mock(SeniorProfile.class);

        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(guardian.getId()).willReturn(1L);
        given(seniorProfileRepository.findByMemberId(10L)).willReturn(Optional.of(seniorProfile));
        given(seniorProfile.getId()).willReturn(100L);
        given(familyConnectionRepository.existsBySeniorIdAndGuardianId(100L, 1L)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> guardianMyPageService.getInviteCode(10L))
                .isInstanceOf(BusinessException.class);
    }

    // ======================== 시니어 전화번호 수정 ========================

    @Test
    @DisplayName("시니어 전화번호를 수정하면 시니어 회원 엔티티의 전화번호가 변경된다")
    void 시니어_전화번호_수정() {
        // given
        Member guardian = mock(Member.class);
        SeniorProfile seniorProfile = mock(SeniorProfile.class);
        Member seniorMember = mock(Member.class);

        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(guardian.getId()).willReturn(1L);
        given(seniorProfileRepository.findByMemberId(10L)).willReturn(Optional.of(seniorProfile));
        given(seniorProfile.getId()).willReturn(100L);
        given(familyConnectionRepository.existsBySeniorIdAndGuardianId(100L, 1L)).willReturn(true);
        given(seniorProfile.getMember()).willReturn(seniorMember);

        // when
        guardianMyPageService.updateSeniorPhone(10L, new UpdatePhoneRequest("01099998888"));

        // then
        verify(seniorMember).updatePhoneNumber("01099998888");
    }

    // ======================== 시니어 프로필 이미지 수정 ========================

    @Test
    @DisplayName("시니어 기존 프로필 이미지가 있을 때 새 이미지로 교체하면 기존 이미지가 S3에서 삭제된다")
    void 시니어_프로필_이미지_수정_기존이미지_삭제() {
        // given
        Member guardian = mock(Member.class);
        SeniorProfile seniorProfile = mock(SeniorProfile.class);
        Member seniorMember = mock(Member.class);

        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(guardian.getId()).willReturn(1L);
        given(seniorProfileRepository.findByMemberId(10L)).willReturn(Optional.of(seniorProfile));
        given(seniorProfile.getId()).willReturn(100L);
        given(familyConnectionRepository.existsBySeniorIdAndGuardianId(100L, 1L)).willReturn(true);
        given(seniorProfile.getMember()).willReturn(seniorMember);
        given(seniorMember.getProfileImage()).willReturn("https://s3.old-senior.png");
        given(s3Service.generateFilePath(anyString(), anyString())).willReturn("profile/new.png");
        given(s3Service.uploadFile(any(), anyString())).willReturn("https://s3.new-senior.png");

        MockMultipartFile image = new MockMultipartFile("image", "new.png", "image/png", new byte[]{1});

        // when
        guardianMyPageService.updateSeniorProfileImage(10L, image);

        // then
        verify(s3Service).deleteFile("https://s3.old-senior.png");
        verify(seniorMember).updateProfileImage("https://s3.new-senior.png");
    }

    @Test
    @DisplayName("시니어 기존 프로필 이미지가 없을 때 새 이미지를 등록하면 S3 삭제 없이 업로드만 진행된다")
    void 시니어_프로필_이미지_수정_기존이미지_없음() {
        // given
        Member guardian = mock(Member.class);
        SeniorProfile seniorProfile = mock(SeniorProfile.class);
        Member seniorMember = mock(Member.class);

        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(guardian.getId()).willReturn(1L);
        given(seniorProfileRepository.findByMemberId(10L)).willReturn(Optional.of(seniorProfile));
        given(seniorProfile.getId()).willReturn(100L);
        given(familyConnectionRepository.existsBySeniorIdAndGuardianId(100L, 1L)).willReturn(true);
        given(seniorProfile.getMember()).willReturn(seniorMember);
        given(seniorMember.getProfileImage()).willReturn(null);
        given(s3Service.generateFilePath(anyString(), anyString())).willReturn("profile/new.png");
        given(s3Service.uploadFile(any(), anyString())).willReturn("https://s3.new-senior.png");

        MockMultipartFile image = new MockMultipartFile("image", "new.png", "image/png", new byte[]{1});

        // when
        guardianMyPageService.updateSeniorProfileImage(10L, image);

        // then
        verify(s3Service, never()).deleteFile(anyString());
        verify(seniorMember).updateProfileImage("https://s3.new-senior.png");
    }

    // ======================== 시니어 주소 수정 ========================

    @Test
    @DisplayName("시니어 주소를 수정하면 시니어 프로필의 주소가 변경된다")
    void 시니어_주소_수정() {
        // given
        Member guardian = mock(Member.class);
        SeniorProfile seniorProfile = mock(SeniorProfile.class);

        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(guardian.getId()).willReturn(1L);
        given(seniorProfileRepository.findByMemberId(10L)).willReturn(Optional.of(seniorProfile));
        given(seniorProfile.getId()).willReturn(100L);
        given(familyConnectionRepository.existsBySeniorIdAndGuardianId(100L, 1L)).willReturn(true);

        // when
        guardianMyPageService.updateSeniorAddress(10L, new UpdateSeniorAddressRequest("서울시 강서구", "101호"));

        // then
        verify(seniorProfile).updateAddress("서울시 강서구", "101호");
    }

    // ======================== 가족 멤버 목록 조회 ========================

    @Test
    @DisplayName("가족 멤버 목록을 조회할 때 현재 사용자가 방장이면 isCurrentUserLeader가 true로 반환된다")
    void 가족_멤버_목록_조회_방장인_경우() {
        // given
        Member guardian = mock(Member.class);
        SeniorProfile seniorProfile = mock(SeniorProfile.class);
        FamilyConnection leaderConnection = mock(FamilyConnection.class);
        FamilyConnection memberConnection = mock(FamilyConnection.class);
        Member leaderGuardian = mock(Member.class);
        Member memberGuardian = mock(Member.class);

        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(guardian.getId()).willReturn(1L);
        given(seniorProfileRepository.findByMemberId(10L)).willReturn(Optional.of(seniorProfile));
        given(seniorProfile.getId()).willReturn(100L);
        given(familyConnectionRepository.existsBySeniorIdAndGuardianId(100L, 1L)).willReturn(true);
        given(familyConnectionRepository.findAllBySeniorIdWithGuardian(100L))
                .willReturn(List.of(leaderConnection, memberConnection));

        given(leaderConnection.getGuardian()).willReturn(leaderGuardian);
        given(leaderGuardian.getId()).willReturn(1L);
        given(leaderGuardian.getName()).willReturn("한채희");
        given(leaderConnection.isLeader()).willReturn(true);

        given(memberConnection.getGuardian()).willReturn(memberGuardian);
        given(memberGuardian.getId()).willReturn(2L);
        given(memberGuardian.getName()).willReturn("한토마");
        given(memberConnection.isLeader()).willReturn(false);

        // when
        FamilyMemberListResponse response = guardianMyPageService.getFamilyMembers(10L);

        // then
        assertThat(response.isCurrentUserLeader()).isTrue();
        assertThat(response.members()).hasSize(2);
    }

    @Test
    @DisplayName("가족 멤버 목록을 조회할 때 현재 사용자가 방장이 아니면 isCurrentUserLeader가 false로 반환된다")
    void 가족_멤버_목록_조회_방장이_아닌_경우() {
        // given
        Member guardian = mock(Member.class);
        SeniorProfile seniorProfile = mock(SeniorProfile.class);
        FamilyConnection leaderConnection = mock(FamilyConnection.class);
        FamilyConnection memberConnection = mock(FamilyConnection.class);
        Member leaderGuardian = mock(Member.class);
        Member memberGuardian = mock(Member.class);

        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(guardian.getId()).willReturn(2L);
        given(seniorProfileRepository.findByMemberId(10L)).willReturn(Optional.of(seniorProfile));
        given(seniorProfile.getId()).willReturn(100L);
        given(familyConnectionRepository.existsBySeniorIdAndGuardianId(100L, 2L)).willReturn(true);
        given(familyConnectionRepository.findAllBySeniorIdWithGuardian(100L))
                .willReturn(List.of(leaderConnection, memberConnection));

        given(leaderConnection.getGuardian()).willReturn(leaderGuardian);
        given(leaderGuardian.getId()).willReturn(1L);
        given(leaderGuardian.getName()).willReturn("한채희");
        given(leaderConnection.isLeader()).willReturn(true);

        given(memberConnection.getGuardian()).willReturn(memberGuardian);
        given(memberGuardian.getId()).willReturn(2L);
        given(memberGuardian.getName()).willReturn("한토마");
        given(memberConnection.isLeader()).willReturn(false);

        // when
        FamilyMemberListResponse response = guardianMyPageService.getFamilyMembers(10L);

        // then
        assertThat(response.isCurrentUserLeader()).isFalse();
        assertThat(response.members()).hasSize(2);
        assertThat(response.members().get(0).name()).isEqualTo("한채희");
        assertThat(response.members().get(1).name()).isEqualTo("한토마");
    }

    // ======================== 방장 변경 ========================

    @Test
    @DisplayName("방장이 다른 보호자를 방장으로 변경하면 기존 방장은 해제되고 새 방장이 설정된다")
    void 방장_변경() {
        // given
        Member guardian = mock(Member.class);
        SeniorProfile seniorProfile = mock(SeniorProfile.class);
        FamilyConnection currentLeader = mock(FamilyConnection.class);
        FamilyConnection newLeader = mock(FamilyConnection.class);
        Member currentGuardian = mock(Member.class);
        Member newGuardianMember = mock(Member.class);

        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(guardian.getId()).willReturn(1L);
        given(seniorProfileRepository.findByMemberId(10L)).willReturn(Optional.of(seniorProfile));
        given(seniorProfile.getId()).willReturn(100L);
        given(familyConnectionRepository.existsBySeniorIdAndGuardianId(100L, 1L)).willReturn(true);
        given(familyConnectionRepository.existsBySeniorIdAndGuardianIdAndIsLeaderTrue(100L, 1L)).willReturn(true);
        given(familyConnectionRepository.findAllBySeniorIdWithGuardian(100L))
                .willReturn(List.of(currentLeader, newLeader));

        given(currentLeader.getGuardian()).willReturn(currentGuardian);
        given(currentGuardian.getId()).willReturn(1L);
        given(newLeader.getGuardian()).willReturn(newGuardianMember);
        given(newGuardianMember.getId()).willReturn(2L);

        // when
        guardianMyPageService.changeLeader(10L, 2L);

        // then
        verify(newLeader).setLeader(true);
        verify(currentLeader).setLeader(false);
    }

    @Test
    @DisplayName("방장을 변경할 때 가족 구성원이 아닌 보호자를 지정하면 예외가 발생한다")
    void 방장_변경_가족_구성원이_아닌_경우() {
        // given
        Member guardian = mock(Member.class);
        SeniorProfile seniorProfile = mock(SeniorProfile.class);
        FamilyConnection connection = mock(FamilyConnection.class);
        Member connectedGuardian = mock(Member.class);

        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(guardian.getId()).willReturn(1L);
        given(seniorProfileRepository.findByMemberId(10L)).willReturn(Optional.of(seniorProfile));
        given(seniorProfile.getId()).willReturn(100L);
        given(familyConnectionRepository.existsBySeniorIdAndGuardianId(100L, 1L)).willReturn(true);
        given(familyConnectionRepository.existsBySeniorIdAndGuardianIdAndIsLeaderTrue(100L, 1L)).willReturn(true);
        given(familyConnectionRepository.findAllBySeniorIdWithGuardian(100L))
                .willReturn(List.of(connection));
        given(connection.getGuardian()).willReturn(connectedGuardian);
        given(connectedGuardian.getId()).willReturn(2L);

        // when & then
        assertThatThrownBy(() -> guardianMyPageService.changeLeader(10L, 999L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("방장이 아닌 보호자가 방장 변경을 시도하면 예외가 발생한다")
    void 방장_변경_방장이_아닌_경우() {
        // given
        Member guardian = mock(Member.class);
        SeniorProfile seniorProfile = mock(SeniorProfile.class);

        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(guardian.getId()).willReturn(1L);
        given(seniorProfileRepository.findByMemberId(10L)).willReturn(Optional.of(seniorProfile));
        given(seniorProfile.getId()).willReturn(100L);
        given(familyConnectionRepository.existsBySeniorIdAndGuardianId(100L, 1L)).willReturn(true);
        given(familyConnectionRepository.existsBySeniorIdAndGuardianIdAndIsLeaderTrue(100L, 1L)).willReturn(false);

        // when & then
        assertThatThrownBy(() -> guardianMyPageService.changeLeader(10L, 2L))
                .isInstanceOf(BusinessException.class);
    }

    // ======================== 가족 멤버 삭제 ========================

    @Test
    @DisplayName("방장이 자기 자신을 가족 멤버에서 삭제하려고 하면 예외가 발생한다")
    void 가족_멤버_삭제_본인_삭제_시도() {
        // given
        Member guardian = mock(Member.class);
        SeniorProfile seniorProfile = mock(SeniorProfile.class);

        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(guardian.getId()).willReturn(1L);
        given(seniorProfileRepository.findByMemberId(10L)).willReturn(Optional.of(seniorProfile));
        given(seniorProfile.getId()).willReturn(100L);
        given(familyConnectionRepository.existsBySeniorIdAndGuardianId(100L, 1L)).willReturn(true);
        given(familyConnectionRepository.existsBySeniorIdAndGuardianIdAndIsLeaderTrue(100L, 1L)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> guardianMyPageService.deleteFamilyMember(10L, 1L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("방장이 다른 보호자를 삭제하면 해당 보호자의 가족 연결이 제거된다")
    void 가족_멤버_삭제() {
        // given
        Member guardian = mock(Member.class);
        SeniorProfile seniorProfile = mock(SeniorProfile.class);
        FamilyConnection targetConnection = mock(FamilyConnection.class);

        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(guardian.getId()).willReturn(1L);
        given(seniorProfileRepository.findByMemberId(10L)).willReturn(Optional.of(seniorProfile));
        given(seniorProfile.getId()).willReturn(100L);
        given(familyConnectionRepository.existsBySeniorIdAndGuardianId(100L, 1L)).willReturn(true);
        given(familyConnectionRepository.existsBySeniorIdAndGuardianIdAndIsLeaderTrue(100L, 1L)).willReturn(true);
        given(familyConnectionRepository.findBySeniorIdAndGuardianId(100L, 2L))
                .willReturn(Optional.of(targetConnection));

        // when
        guardianMyPageService.deleteFamilyMember(10L, 2L);

        // then
        verify(familyConnectionRepository).delete(targetConnection);
    }
}
