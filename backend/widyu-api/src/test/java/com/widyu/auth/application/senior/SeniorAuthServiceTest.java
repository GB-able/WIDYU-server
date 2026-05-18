package com.widyu.auth.application.senior;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.widyu.auth.dto.request.SeniorSignInRequest;
import com.widyu.auth.dto.request.SeniorSignUpRequest;
import com.widyu.auth.dto.response.TokenPairResponse;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.security.JwtTokenProvider;
import com.widyu.global.util.MemberUtil;
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
    void 시니어_일괄_등록_시_멤버_프로필_가족연결_모두_저장() {
        // given
        Member guardian = Member.createMember(MemberType.GUARDIAN, "보호자", "01099999999");
        ReflectionTestUtils.setField(guardian, "id", 1L);

        List<SeniorSignUpRequest> requests = List.of(
                new SeniorSignUpRequest("부모님", "01011112222", "서울시 강남구", "101호", "1234567"),
                new SeniorSignUpRequest("할머니", "01033334444", "서울시 서초구", "202호", "7654321")
        );

        Member seniorMember1 = Member.createMember(MemberType.SENIOR, "부모님", "01011112222");
        Member seniorMember2 = Member.createMember(MemberType.SENIOR, "할머니", "01033334444");

        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(memberRepository.saveAll(anyList())).willReturn(List.of(seniorMember1, seniorMember2));
        given(seniorProfileRepository.existsByFamilyCode(anyString())).willReturn(false);
        given(seniorProfileRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(familyConnectionRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

        // when
        seniorAuthService.seniorSignUpBulk(requests);

        // then
        verify(memberRepository).saveAll(anyList());
        verify(seniorProfileRepository).saveAll(anyList());
        verify(familyConnectionRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("빈 리스트로 시니어 등록 시 BusinessException을 던진다")
    void 빈_리스트로_시니어_등록_시_예외가_발생한다() {
        // given
        Member guardian = Member.createMember(MemberType.GUARDIAN, "보호자", "01099999999");
        given(memberUtil.getCurrentMember()).willReturn(guardian);

        // when & then
        assertThatThrownBy(() -> seniorAuthService.seniorSignUpBulk(List.of()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SENIOR_SIGNUP_REQUEST_EMPTY);
    }

    @Test
    @DisplayName("null 리스트로 시니어 등록 시 BusinessException을 던진다")
    void null_리스트로_시니어_등록_시_예외가_발생한다() {
        // given
        Member guardian = Member.createMember(MemberType.GUARDIAN, "보호자", "01099999999");
        given(memberUtil.getCurrentMember()).willReturn(guardian);

        // when & then
        assertThatThrownBy(() -> seniorAuthService.seniorSignUpBulk(null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SENIOR_SIGNUP_REQUEST_EMPTY);
    }

    @Test
    @DisplayName("유효한 초대코드와 전화번호로 시니어 로그인 시 토큰 쌍을 반환한다")
    void 유효한_초대코드와_전화번호로_로그인_시_토큰쌍을_반환한다() {
        // given
        String inviteCode = "1234567";
        String phone = "01011112222";
        Member seniorMember = Member.createMember(MemberType.SENIOR, "부모님", phone);
        ReflectionTestUtils.setField(seniorMember, "id", 2L);

        SeniorProfile seniorProfile = SeniorProfile.createSeniorProfile(
                seniorMember, "서울", "101호", inviteCode, "ABC123"
        );
        TokenPairResponse expectedToken = TokenPairResponse.of(2L, "access", "refresh");

        given(seniorProfileRepository.findByInviteCodeAndMemberPhoneNumber(inviteCode, phone))
                .willReturn(Optional.of(seniorProfile));
        given(jwtTokenProvider.generateTokenPair(any(), any(), any())).willReturn(expectedToken);

        // when
        TokenPairResponse result = seniorAuthService.seniorSignIn(new SeniorSignInRequest(inviteCode, phone));

        // then
        assertThat(result).isEqualTo(expectedToken);
    }

    @Test
    @DisplayName("유효하지 않은 초대코드로 로그인 시 BusinessException을 던진다")
    void 유효하지_않은_초대코드로_로그인_시_예외가_발생한다() {
        // given
        given(seniorProfileRepository.findByInviteCodeAndMemberPhoneNumber("0000000", "01011112222"))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> seniorAuthService.seniorSignIn(
                new SeniorSignInRequest("0000000", "01011112222")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVITE_CODE_NOT_FOUND);
    }

    @Test
    @DisplayName("가족 코드 생성 최대 시도 초과 시 BusinessException을 던진다")
    void 가족코드_생성_최대_시도_초과_시_예외가_발생한다() {
        // given
        Member guardian = Member.createMember(MemberType.GUARDIAN, "보호자", "01099999999");
        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(memberRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(seniorProfileRepository.existsByFamilyCode(anyString())).willReturn(true);

        List<SeniorSignUpRequest> requests = List.of(
                new SeniorSignUpRequest("부모님", "01011112222", "서울", "101호", "1234567")
        );

        // when & then
        assertThatThrownBy(() -> seniorAuthService.seniorSignUpBulk(requests))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FAMILY_CODE_GENERATION_FAILED);
    }

    @Test
    @DisplayName("시니어 일괄 등록 시 요청 개수만큼 멤버가 생성된다")
    void 시니어_일괄_등록_시_요청_개수만큼_멤버가_생성된다() {
        // given
        Member guardian = Member.createMember(MemberType.GUARDIAN, "보호자", "01099999999");
        given(memberUtil.getCurrentMember()).willReturn(guardian);
        given(seniorProfileRepository.existsByFamilyCode(anyString())).willReturn(false);

        List<SeniorSignUpRequest> requests = List.of(
                new SeniorSignUpRequest("부모님", "01011112222", "서울", "101호", "1234567"),
                new SeniorSignUpRequest("할머니", "01033334444", "부산", "202호", "7654321")
        );

        given(memberRepository.saveAll(anyList())).willAnswer(inv -> {
            List<Member> members = inv.getArgument(0);
            assertThat(members).hasSize(2);
            return members;
        });
        given(seniorProfileRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(familyConnectionRepository.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

        // when
        seniorAuthService.seniorSignUpBulk(requests);

        // then
        verify(memberRepository).saveAll(anyList());
    }
}
