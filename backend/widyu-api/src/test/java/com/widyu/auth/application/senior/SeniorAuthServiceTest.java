package com.widyu.auth.application.senior;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.widyu.auth.dto.request.SeniorSignInRequest;
import com.widyu.auth.dto.request.SeniorSignUpRequest;
import com.widyu.auth.dto.response.TokenPairResponse;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.security.JwtTokenProvider;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.FamilyConnection;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyConnectionRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("SeniorAuthService 단위 테스트")
class SeniorAuthServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private SeniorProfileRepository seniorProfileRepository;
    @Mock private FamilyConnectionRepository familyConnectionRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private MemberUtil memberUtil;

    @InjectMocks
    private SeniorAuthService seniorAuthService;

    @Test
    @DisplayName("시니어 일괄 등록 시 멤버, 프로필, 가족 연결이 모두 저장된다")
    void seniorSignUpBulk_validRequests_savesAllEntities() {
        // given
        Member guardian = Member.createMember(MemberType.GUARDIAN, "보호자", "01099999999");
        ReflectionTestUtils.setField(guardian, "id", 1L);

        List<SeniorSignUpRequest> requests = List.of(
                new SeniorSignUpRequest("부모님", "19500101", "01011112222", "서울시 강남구", "101호", "INVITE001"),
                new SeniorSignUpRequest("할머니", "19450505", "01033334444", "서울시 서초구", "202호", "INVITE002")
        );

        Member seniorMember1 = Member.createMember(MemberType.SENIOR, "부모님", "01011112222");
        Member seniorMember2 = Member.createMember(MemberType.SENIOR, "할머니", "01033334444");

        when(memberUtil.getCurrentMember()).thenReturn(guardian);
        when(memberRepository.saveAll(anyList())).thenReturn(List.of(seniorMember1, seniorMember2));
        when(seniorProfileRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(familyConnectionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        // when
        seniorAuthService.seniorSignUpBulk(requests);

        // then
        verify(memberRepository).saveAll(anyList());
        verify(seniorProfileRepository).saveAll(anyList());
        verify(familyConnectionRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("빈 리스트로 시니어 등록 시 BusinessException을 던진다")
    void seniorSignUpBulk_emptyList_throwsBusinessException() {
        // given
        Member guardian = Member.createMember(MemberType.GUARDIAN, "보호자", "01099999999");
        when(memberUtil.getCurrentMember()).thenReturn(guardian);

        // when & then
        assertThatThrownBy(() -> seniorAuthService.seniorSignUpBulk(List.of()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("null 리스트로 시니어 등록 시 BusinessException을 던진다")
    void seniorSignUpBulk_nullList_throwsBusinessException() {
        // given
        Member guardian = Member.createMember(MemberType.GUARDIAN, "보호자", "01099999999");
        when(memberUtil.getCurrentMember()).thenReturn(guardian);

        // when & then
        assertThatThrownBy(() -> seniorAuthService.seniorSignUpBulk(null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("유효한 초대코드와 전화번호로 시니어 로그인 시 토큰 쌍을 반환한다")
    void seniorSignIn_validCredentials_returnsTokenPair() {
        // given
        String inviteCode = "INVITE001";
        String phone = "01011112222";
        Member seniorMember = Member.createMember(MemberType.SENIOR, "부모님", phone);
        ReflectionTestUtils.setField(seniorMember, "id", 2L);

        SeniorProfile seniorProfile = SeniorProfile.createSeniorProfile(
                seniorMember, "19500101", "서울", "101호", inviteCode
        );
        TokenPairResponse expectedToken = TokenPairResponse.of(2L, "access", "refresh");

        when(seniorProfileRepository.findByInviteCodeAndMemberPhoneNumber(inviteCode, phone))
                .thenReturn(Optional.of(seniorProfile));
        when(jwtTokenProvider.generateTokenPair(any(), any(), eq("senior"))).thenReturn(expectedToken);

        // when
        TokenPairResponse result = seniorAuthService.seniorSignIn(new SeniorSignInRequest(inviteCode, phone));

        // then
        assertThat(result).isEqualTo(expectedToken);
    }

    @Test
    @DisplayName("유효하지 않은 초대코드로 로그인 시 BusinessException을 던진다")
    void seniorSignIn_invalidInviteCode_throwsBusinessException() {
        // given
        when(seniorProfileRepository.findByInviteCodeAndMemberPhoneNumber("WRONG_CODE", "01011112222"))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> seniorAuthService.seniorSignIn(
                new SeniorSignInRequest("WRONG_CODE", "01011112222")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVITE_CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("시니어 일괄 등록 시 요청 개수만큼 멤버가 생성된다")
    void seniorSignUpBulk_twoRequests_createsTwoMembers() {
        // given
        Member guardian = Member.createMember(MemberType.GUARDIAN, "보호자", "01099999999");
        when(memberUtil.getCurrentMember()).thenReturn(guardian);

        List<SeniorSignUpRequest> requests = List.of(
                new SeniorSignUpRequest("부모님", "19500101", "01011112222", "서울", "101호", "INVITE001"),
                new SeniorSignUpRequest("할머니", "19450505", "01033334444", "부산", "202호", "INVITE002")
        );

        when(memberRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<Member> members = inv.getArgument(0);
            assertThat(members).hasSize(2);
            return members;
        });
        when(seniorProfileRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(familyConnectionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        // when
        seniorAuthService.seniorSignUpBulk(requests);
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
