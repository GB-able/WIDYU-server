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
import com.widyu.member.Member;
import com.widyu.member.PointHistory;
import com.widyu.member.PointHistoryType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyConnectionRepository;
import com.widyu.member.repository.PointHistoryRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import com.widyu.mypage.dto.request.UpdateNameRequest;
import com.widyu.mypage.dto.request.UpdatePhoneRequest;
import com.widyu.mypage.dto.response.EmergencyContactResponse;
import com.widyu.mypage.dto.response.FamilyCodeResponse;
import com.widyu.mypage.dto.response.PointHistoryResponse;
import com.widyu.mypage.dto.response.SeniorInfoResponse;
import com.widyu.mypage.dto.response.SeniorProfileDetailResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class SeniorMyPageServiceTest {

    @Mock private MemberUtil memberUtil;
    @Mock private S3Service s3Service;
    @Mock private SeniorProfileRepository seniorProfileRepository;
    @Mock private PointHistoryRepository pointHistoryRepository;
    @Mock private FamilyConnectionRepository familyConnectionRepository;

    @InjectMocks
    private SeniorMyPageService seniorMyPageService;

    // ======================== 내 정보 조회 ========================

    @Test
    @DisplayName("시니어 내 정보를 조회하면 프로필 이미지, 이름, 포인트를 반환한다")
    void 시니어_내정보_조회() {
        // given
        Member member = mock(Member.class);
        SeniorProfile seniorProfile = mock(SeniorProfile.class);

        given(memberUtil.getCurrentMember()).willReturn(member);
        given(member.getId()).willReturn(1L);
        given(member.getProfileImage()).willReturn("image.png");
        given(member.getName()).willReturn("오일남");
        given(member.getSeniorProfile()).willReturn(seniorProfile);
        given(seniorProfile.getPoints()).willReturn(500L);

        // when
        SeniorInfoResponse response = seniorMyPageService.getSeniorInfo();

        // then
        assertThat(response.name()).isEqualTo("오일남");
        assertThat(response.profileImage()).isEqualTo("image.png");
        assertThat(response.points()).isEqualTo(500L);
    }

    // ======================== 가족코드 조회 ========================

    @Test
    @DisplayName("가족코드를 조회하면 시니어 프로필에 등록된 6자리 코드를 반환한다")
    void 가족코드_조회() {
        // given
        Member member = mock(Member.class);
        SeniorProfile seniorProfile = mock(SeniorProfile.class);

        given(memberUtil.getCurrentMember()).willReturn(member);
        given(member.getSeniorProfile()).willReturn(seniorProfile);
        given(seniorProfile.getFamilyCode()).willReturn("AB12CD");

        // when
        FamilyCodeResponse response = seniorMyPageService.getFamilyCode();

        // then
        assertThat(response.familyCode()).isEqualTo("AB12CD");
    }

    // ======================== 프로필 설정 조회 ========================

    @Test
    @DisplayName("프로필 설정을 조회하면 이름, 생년월일, 전화번호, 주소, 초대코드를 반환한다")
    void 프로필_설정_조회() {
        // given
        Member member = mock(Member.class);
        SeniorProfile seniorProfile = mock(SeniorProfile.class);

        given(memberUtil.getCurrentMember()).willReturn(member);
        given(member.getSeniorProfile()).willReturn(seniorProfile);
        given(member.getName()).willReturn("오일남");
        given(member.getPhoneNumber()).willReturn("01012345678");
        given(member.getProfileImage()).willReturn("image.png");
        given(seniorProfile.getBirthDate()).willReturn("19680605");
        given(seniorProfile.getAddress()).willReturn("서울시 강서구");
        given(seniorProfile.getDetailAddress()).willReturn("101호");
        given(seniorProfile.getInviteCode()).willReturn("1234567");

        // when
        SeniorProfileDetailResponse response = seniorMyPageService.getProfileDetail();

        // then
        assertThat(response.name()).isEqualTo("오일남");
        assertThat(response.birthDate()).isEqualTo("19680605");
        assertThat(response.inviteCode()).isEqualTo("1234567");
    }

    // ======================== 이름 수정 ========================

    @Test
    @DisplayName("이름을 수정하면 회원 엔티티의 이름이 변경된다")
    void 이름_수정() {
        // given
        Member member = mock(Member.class);
        given(memberUtil.getCurrentMember()).willReturn(member);

        // when
        seniorMyPageService.updateName(new UpdateNameRequest("새이름"));

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
        given(member.getProfileImage()).willReturn("https://s3.old-image.png");
        given(s3Service.generateFilePath(anyString(), anyString())).willReturn("profile/new.png");
        given(s3Service.uploadFile(any(), anyString())).willReturn("https://s3.new-image.png");

        MockMultipartFile image = new MockMultipartFile("image", "new.png", "image/png", new byte[]{1});

        // when
        seniorMyPageService.updateProfileImage(image);

        // then
        verify(s3Service).deleteFile("https://s3.old-image.png");
        verify(member).updateProfileImage("https://s3.new-image.png");
    }

    @Test
    @DisplayName("기존 프로필 이미지가 없을 때 새 이미지를 등록하면 S3 삭제 없이 업로드만 진행된다")
    void 프로필_이미지_수정_기존이미지_없음() {
        // given
        Member member = mock(Member.class);
        given(memberUtil.getCurrentMember()).willReturn(member);
        given(member.getProfileImage()).willReturn(null);
        given(s3Service.generateFilePath(anyString(), anyString())).willReturn("profile/new.png");
        given(s3Service.uploadFile(any(), anyString())).willReturn("https://s3.new-image.png");

        MockMultipartFile image = new MockMultipartFile("image", "new.png", "image/png", new byte[]{1});

        // when
        seniorMyPageService.updateProfileImage(image);

        // then
        verify(s3Service, never()).deleteFile(anyString());
        verify(member).updateProfileImage("https://s3.new-image.png");
    }

    // ======================== 전화번호 수정 ========================

    @Test
    @DisplayName("전화번호를 수정하면 회원 엔티티의 전화번호가 변경된다")
    void 전화번호_수정() {
        // given
        Member member = mock(Member.class);
        given(memberUtil.getCurrentMember()).willReturn(member);

        // when
        seniorMyPageService.updatePhoneNumber(new UpdatePhoneRequest("01099998888"));

        // then
        verify(member).updatePhoneNumber("01099998888");
    }

    // ======================== 포인트 내역 조회 ========================

    @Test
    @DisplayName("포인트 내역을 조회하면 현재 포인트와 적립/사용 내역 목록을 최신순으로 반환한다")
    void 포인트_내역_조회() {
        // given
        Member member = mock(Member.class);
        SeniorProfile seniorProfile = mock(SeniorProfile.class);
        PointHistory earn = mock(PointHistory.class);
        PointHistory use = mock(PointHistory.class);

        given(memberUtil.getCurrentMember()).willReturn(member);
        given(member.getSeniorProfile()).willReturn(seniorProfile);
        given(seniorProfile.getId()).willReturn(1L);
        given(seniorProfile.getPoints()).willReturn(490L);
        given(pointHistoryRepository.findAllBySeniorProfileIdOrderByCreatedAtDesc(1L))
                .willReturn(List.of(earn, use));

        given(earn.getType()).willReturn(PointHistoryType.EARN);
        given(earn.getAmount()).willReturn(50L);
        given(earn.getDescription()).willReturn("포인트 적립");
        given(earn.getCreatedAt()).willReturn(LocalDateTime.now());

        given(use.getType()).willReturn(PointHistoryType.USE);
        given(use.getAmount()).willReturn(50L);
        given(use.getDescription()).willReturn("앨범 해금");
        given(use.getCreatedAt()).willReturn(LocalDateTime.now().minusHours(1));

        // when
        PointHistoryResponse response = seniorMyPageService.getPointHistory();

        // then
        assertThat(response.currentPoints()).isEqualTo(490L);
        assertThat(response.histories()).hasSize(2);
        assertThat(response.histories().get(0).type()).isEqualTo(PointHistoryType.EARN);
        assertThat(response.histories().get(1).type()).isEqualTo(PointHistoryType.USE);
    }

    // ======================== 비상연락처 조회 ========================

    @Test
    @DisplayName("비상연락처를 조회할 때 대표 보호자가 지정되어 있으면 대표 연락처 정보를 함께 반환한다")
    void 비상연락처_조회_대표연락처_있음() {
        // given
        Member member = mock(Member.class);
        SeniorProfile seniorProfile = mock(SeniorProfile.class);
        FamilyConnection repConnection = mock(FamilyConnection.class);
        Member guardian = mock(Member.class);

        given(memberUtil.getCurrentMember()).willReturn(member);
        given(member.getSeniorProfile()).willReturn(seniorProfile);
        given(seniorProfile.getId()).willReturn(1L);
        given(familyConnectionRepository.findAllBySeniorIdWithGuardian(1L))
                .willReturn(List.of(repConnection));
        given(repConnection.getGuardian()).willReturn(guardian);
        given(repConnection.isRepresentative()).willReturn(true);
        given(guardian.getId()).willReturn(10L);
        given(guardian.getName()).willReturn("한토마");
        given(guardian.getPhoneNumber()).willReturn("01011112222");

        // when
        EmergencyContactResponse response = seniorMyPageService.getEmergencyContacts();

        // then
        assertThat(response.representative()).isNotNull();
        assertThat(response.representative().name()).isEqualTo("한토마");
        assertThat(response.representative().phoneNumber()).isEqualTo("01011112222");
    }

    @Test
    @DisplayName("비상연락처를 조회할 때 대표 보호자가 지정되지 않으면 대표 연락처는 null로 반환한다")
    void 비상연락처_조회_대표연락처_없음() {
        // given
        Member member = mock(Member.class);
        SeniorProfile seniorProfile = mock(SeniorProfile.class);
        FamilyConnection connection = mock(FamilyConnection.class);
        Member guardian = mock(Member.class);

        given(memberUtil.getCurrentMember()).willReturn(member);
        given(member.getSeniorProfile()).willReturn(seniorProfile);
        given(seniorProfile.getId()).willReturn(1L);
        given(familyConnectionRepository.findAllBySeniorIdWithGuardian(1L))
                .willReturn(List.of(connection));
        given(connection.getGuardian()).willReturn(guardian);
        given(connection.isRepresentative()).willReturn(false);
        given(guardian.getId()).willReturn(10L);
        given(guardian.getName()).willReturn("한토마");
        given(guardian.getPhoneNumber()).willReturn("01011112222");

        // when
        EmergencyContactResponse response = seniorMyPageService.getEmergencyContacts();

        // then
        assertThat(response.representative()).isNull();
        assertThat(response.familyMembers()).hasSize(1);
    }

    // ======================== 대표 비상연락처 변경 ========================

    @Test
    @DisplayName("대표 비상연락처를 변경하면 선택한 보호자가 대표로 설정되고 기존 대표는 해제된다")
    void 대표_비상연락처_변경() {
        // given
        Member member = mock(Member.class);
        SeniorProfile seniorProfile = mock(SeniorProfile.class);
        FamilyConnection target = mock(FamilyConnection.class);
        FamilyConnection other = mock(FamilyConnection.class);
        Member targetGuardian = mock(Member.class);
        Member otherGuardian = mock(Member.class);

        given(memberUtil.getCurrentMember()).willReturn(member);
        given(member.getSeniorProfile()).willReturn(seniorProfile);
        given(seniorProfile.getId()).willReturn(1L);
        given(familyConnectionRepository.findAllBySeniorIdWithGuardian(1L))
                .willReturn(List.of(target, other));
        given(target.getGuardian()).willReturn(targetGuardian);
        given(targetGuardian.getId()).willReturn(10L);
        given(other.getGuardian()).willReturn(otherGuardian);
        given(otherGuardian.getId()).willReturn(20L);

        // when
        seniorMyPageService.updateRepresentativeContact(10L);

        // then
        verify(target).setRepresentative(true);
        verify(other).setRepresentative(false);
    }

    @Test
    @DisplayName("가족 구성원이 아닌 보호자를 대표 연락처로 지정하면 예외가 발생한다")
    void 대표_비상연락처_변경_존재하지않는_보호자() {
        // given
        Member member = mock(Member.class);
        SeniorProfile seniorProfile = mock(SeniorProfile.class);
        FamilyConnection connection = mock(FamilyConnection.class);
        Member guardian = mock(Member.class);

        given(memberUtil.getCurrentMember()).willReturn(member);
        given(member.getSeniorProfile()).willReturn(seniorProfile);
        given(seniorProfile.getId()).willReturn(1L);
        given(familyConnectionRepository.findAllBySeniorIdWithGuardian(1L))
                .willReturn(List.of(connection));
        given(connection.getGuardian()).willReturn(guardian);
        given(guardian.getId()).willReturn(20L);

        // when & then
        assertThatThrownBy(() -> seniorMyPageService.updateRepresentativeContact(999L))
                .isInstanceOf(BusinessException.class);
    }
}
