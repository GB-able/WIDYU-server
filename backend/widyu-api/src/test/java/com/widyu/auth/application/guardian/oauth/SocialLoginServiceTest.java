package com.widyu.auth.application.guardian.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import com.widyu.auth.OAuthProvider;
import com.widyu.auth.application.SocialTemporaryTokenService;
import com.widyu.auth.application.guardian.oauth.strategy.SocialLoginStrategy;
import com.widyu.auth.application.guardian.oauth.strategy.SocialLoginStrategyFactory;
import com.widyu.auth.application.guardian.oauth.strategy.UserInfo;
import com.widyu.auth.dto.SocialTemporaryTokenDto;
import com.widyu.auth.dto.request.AppleSignUpRequest;
import com.widyu.auth.dto.request.SocialLoginRequest;
import com.widyu.auth.dto.response.ApplePhoneNumberResponse;
import com.widyu.auth.dto.response.SocialClientResponse;
import com.widyu.auth.dto.response.SocialLoginResponse;
import com.widyu.auth.dto.response.TokenPairResponse;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.security.JwtTokenProvider;
import com.widyu.global.util.MemberUtil;
import com.widyu.global.util.TemporaryMemberUtil;
import com.widyu.member.Member;
import com.widyu.member.MemberRole;
import com.widyu.member.MemberType;
import com.widyu.member.SocialAccount;
import com.widyu.member.repository.MemberRepository;
import com.widyu.auth.TemporaryMember;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
@DisplayName("SocialLoginService 단위 테스트")
class SocialLoginServiceTest {

    @Mock private SocialLoginStrategyFactory strategyFactory;
    @Mock private MemberRepository memberRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private TemporaryMemberUtil temporaryMemberUtil;
    @Mock private SocialTemporaryTokenService socialTemporaryTokenService;
    @Mock private MemberUtil memberUtil;
    @Mock private AppleAccountMergeService appleAccountMergeService;

    @InjectMocks
    private SocialLoginService socialLoginService;

    @Test
    @DisplayName("소셜 임시 토큰 헤더 없이 계정 연동 시도 시 BusinessException을 던진다")
    void 소셜_임시_토큰_헤더_없이_연동_시도_시_예외가_발생한다() {
        // given
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        given(httpRequest.getHeader(AUTHORIZATION)).willReturn(null);

        // when & then
        assertThatThrownBy(() -> socialLoginService.integrateSocialAccount(httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MISSING_SOCIAL_TEMPORARY_TOKEN);
    }

    @Test
    @DisplayName("이미 연동된 소셜 제공자 계정 연동 시도 시 BusinessException을 던진다")
    void 이미_연동된_소셜_제공자_연동_시도_시_예외가_발생한다() {
        // given
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        given(httpRequest.getHeader(AUTHORIZATION)).willReturn("Bearer social-temp-token");

        SocialTemporaryTokenDto tokenDto = new SocialTemporaryTokenDto(1L, "kakao", "oauth123", "test@kakao.com");
        given(socialTemporaryTokenService.validateAndRetrieve("social-temp-token")).willReturn(tokenDto);

        Member member = mock(Member.class);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));

        SocialAccount kakaoAccount = mock(SocialAccount.class);
        given(kakaoAccount.getProvider()).willReturn("kakao");
        given(member.getSocialAccounts()).willReturn(List.of(kakaoAccount));

        // when & then
        assertThatThrownBy(() -> socialLoginService.integrateSocialAccount(httpRequest))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SOCIAL_PROVIDER_ALREADY_LINKED);
    }

    @Test
    @DisplayName("유효한 소셜 임시 토큰으로 계정 연동 시 토큰 쌍이 반환된다")
    void 유효한_소셜_임시_토큰으로_계정_연동_시_토큰_쌍이_반환된다() {
        // given
        HttpServletRequest httpRequest = mock(HttpServletRequest.class);
        given(httpRequest.getHeader(AUTHORIZATION)).willReturn("Bearer social-temp-token");

        SocialTemporaryTokenDto tokenDto = new SocialTemporaryTokenDto(1L, "kakao", "oauth123", "test@kakao.com");
        given(socialTemporaryTokenService.validateAndRetrieve("social-temp-token")).willReturn(tokenDto);

        Member member = mock(Member.class);
        given(member.getId()).willReturn(1L);
        given(memberRepository.findById(1L)).willReturn(Optional.of(member));
        given(member.getSocialAccounts()).willReturn(new ArrayList<>());

        given(memberRepository.findBySocialAccounts_EmailAndSocialAccounts_Provider("test@kakao.com", "kakao"))
                .willReturn(Optional.empty());

        TokenPairResponse tokenPair = TokenPairResponse.of(1L, "access-token", "refresh-token");
        given(jwtTokenProvider.generateTokenPair(1L, MemberRole.USER, "kakao")).willReturn(tokenPair);

        // when
        TokenPairResponse response = socialLoginService.integrateSocialAccount(httpRequest);

        // then
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        verify(socialTemporaryTokenService).deleteSocialTemporaryToken("social-temp-token");
    }

    @Test
    @DisplayName("액세스 토큰 회원이 번호가 빈 애플 회원이면 임시 토큰의 번호로 채우고 임시 회원을 삭제한다")
    void 액세스_토큰_회원이_번호가_빈_애플_회원이면_번호를_채우고_임시_회원을_삭제한다() {
        // given
        AppleSignUpRequest request = new AppleSignUpRequest("temp-token");
        TemporaryMember temporaryMember = TemporaryMember.createTemporaryMember("애플유저", "01099999999");
        given(temporaryMemberUtil.getTemporaryMember("temp-token")).willReturn(temporaryMember);
        given(memberUtil.getCurrentMember()).willReturn(appleMember(1L, null));
        given(memberRepository.fillPhoneNumberIfEmpty(1L, "01099999999")).willReturn(1);

        // when
        ApplePhoneNumberResponse response = socialLoginService.updatePhoneNumberIfAppleSignUp(request);

        // then
        assertThat(response.merged()).isFalse();
        assertThat(response.accessToken()).isNull();
        verify(memberRepository).fillPhoneNumberIfEmpty(1L, "01099999999");
        verify(temporaryMemberUtil).deleteTemporaryMember(temporaryMember.getId());
        verifyNoInteractions(appleAccountMergeService);
    }

    @Test
    @DisplayName("트랜잭션 안에서 애플 전화번호를 보강하면 임시 회원은 커밋 뒤에 삭제한다")
    void 트랜잭션_안에서_보강하면_임시_회원은_커밋_뒤에_삭제한다() {
        // given
        AppleSignUpRequest request = new AppleSignUpRequest("temp-token");
        TemporaryMember temporaryMember = TemporaryMember.createTemporaryMember("애플유저", "01099999999");
        given(temporaryMemberUtil.getTemporaryMember("temp-token")).willReturn(temporaryMember);
        given(memberUtil.getCurrentMember()).willReturn(appleMember(1L, null));
        given(memberRepository.fillPhoneNumberIfEmpty(1L, "01099999999")).willReturn(1);

        TransactionSynchronizationManager.initSynchronization();
        try {
            // when
            socialLoginService.updatePhoneNumberIfAppleSignUp(request);

            // then
            verify(temporaryMemberUtil, never()).deleteTemporaryMember(any());
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(temporaryMemberUtil).deleteTemporaryMember(temporaryMember.getId());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("이미 전화번호가 있는 애플 회원에게 보강하면 FORBIDDEN 예외가 발생하고 임시 회원이 그대로 남는다")
    void 이미_전화번호가_있는_애플_회원에게_보강하면_예외가_발생하고_임시_회원이_남는다() {
        // given
        AppleSignUpRequest request = new AppleSignUpRequest("temp-token");
        TemporaryMember temporaryMember = TemporaryMember.createTemporaryMember("다른사람", "01099999999");
        given(temporaryMemberUtil.getTemporaryMember("temp-token")).willReturn(temporaryMember);
        given(memberUtil.getCurrentMember()).willReturn(appleMember(1L, "01011111111"));

        // when & then
        assertThatThrownBy(() -> socialLoginService.updatePhoneNumberIfAppleSignUp(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
        verify(memberRepository, never()).fillPhoneNumberIfEmpty(any(), any());
        verify(temporaryMemberUtil, never()).deleteTemporaryMember(any());
        verifyNoInteractions(appleAccountMergeService);
    }

    @Test
    @DisplayName("빈 번호를 채우는 조건부 갱신이 0건이면 동시 요청이 먼저 채운 것으로 보고 FORBIDDEN 예외가 발생한다")
    void 빈_번호_채우기가_0건이면_예외가_발생한다() {
        // given
        AppleSignUpRequest request = new AppleSignUpRequest("temp-token");
        TemporaryMember temporaryMember = TemporaryMember.createTemporaryMember("애플유저", "01099999999");
        given(temporaryMemberUtil.getTemporaryMember("temp-token")).willReturn(temporaryMember);
        given(memberUtil.getCurrentMember()).willReturn(appleMember(1L, null));
        given(memberRepository.fillPhoneNumberIfEmpty(1L, "01099999999")).willReturn(0);

        // when & then
        assertThatThrownBy(() -> socialLoginService.updatePhoneNumberIfAppleSignUp(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
        verify(temporaryMemberUtil, never()).deleteTemporaryMember(any());
    }

    @Test
    @DisplayName("임시 토큰이 유효하지 않으면 회원을 조회하지 않고 UNAUTHORIZED 예외가 발생한다")
    void 임시_토큰이_유효하지_않으면_회원을_조회하지_않는다() {
        // given
        AppleSignUpRequest request = new AppleSignUpRequest("bad-token");
        given(temporaryMemberUtil.getTemporaryMember("bad-token"))
                .willThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        // when & then
        assertThatThrownBy(() -> socialLoginService.updatePhoneNumberIfAppleSignUp(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(memberUtil, memberRepository);
    }

    @Test
    @DisplayName("액세스 토큰 없이 애플 전화번호를 보강하면 UNAUTHORIZED 예외가 발생하고 번호를 채우지 않는다")
    void 액세스_토큰_없이_보강하면_번호를_채우지_않는다() {
        // given
        AppleSignUpRequest request = new AppleSignUpRequest("temp-token");
        TemporaryMember temporaryMember = TemporaryMember.createTemporaryMember("애플유저", "01099999999");
        given(temporaryMemberUtil.getTemporaryMember("temp-token")).willReturn(temporaryMember);
        given(memberUtil.getCurrentMember()).willThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        // when & then
        assertThatThrownBy(() -> socialLoginService.updatePhoneNumberIfAppleSignUp(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED);
        verifyNoInteractions(memberRepository);
        verify(temporaryMemberUtil, never()).deleteTemporaryMember(any());
    }

    @Test
    @DisplayName("액세스 토큰 회원에게 애플 계정이 없으면 FORBIDDEN 예외가 발생하고 번호와 임시 회원이 바뀌지 않는다")
    void 애플_계정이_없는_회원이_보강하면_거부한다() {
        // given
        AppleSignUpRequest request = new AppleSignUpRequest("temp-token");
        TemporaryMember temporaryMember = TemporaryMember.createTemporaryMember("카카오유저", "01099999999");
        given(temporaryMemberUtil.getTemporaryMember("temp-token")).willReturn(temporaryMember);
        Member kakaoMember = Member.createMember(MemberType.GUARDIAN, "카카오유저", null);
        ReflectionTestUtils.setField(kakaoMember, "id", 2L);
        kakaoMember.getSocialAccounts().add(
                SocialAccount.createSocialAccount("kakao@kakao.com", "KAKAO", "kakao-oauth-id", null, kakaoMember));
        given(memberUtil.getCurrentMember()).willReturn(kakaoMember);

        // when & then
        assertThatThrownBy(() -> socialLoginService.updatePhoneNumberIfAppleSignUp(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
        verifyNoInteractions(memberRepository);
        verify(temporaryMemberUtil, never()).deleteTemporaryMember(any());
    }

    @Test
    @DisplayName("기존 소셜 회원 로그인 시 토큰 쌍이 반환된다")
    void 기존_소셜_회원_로그인_시_토큰_쌍이_반환된다() {
        // given
        SocialLoginRequest request = mock(SocialLoginRequest.class);

        SocialLoginStrategy strategy = mock(SocialLoginStrategy.class);
        given(strategyFactory.getStrategy("kakao")).willReturn(strategy);
        given(strategy.getSupportedProvider()).willReturn(OAuthProvider.KAKAO);

        SocialClientResponse socialResponse = mock(SocialClientResponse.class);
        given(socialResponse.oauthId()).willReturn("oauth123");
        given(strategy.getUserInfo(request)).willReturn(socialResponse);
        given(strategy.enrichWithRefreshToken(socialResponse, request)).willReturn(socialResponse);

        UserInfo userInfo = mock(UserInfo.class);
        given(strategy.processUserInfo(socialResponse, request)).willReturn(userInfo);

        // 기존 회원 존재
        Member existingMember = mock(Member.class);
        given(existingMember.getId()).willReturn(1L);
        given(memberRepository.findMemberIdByProviderAndOauthId("kakao", "oauth123"))
                .willReturn(Optional.of(1L));
        given(memberRepository.findWithAllAccountsById(1L)).willReturn(Optional.of(existingMember));

        SocialAccount kakaoAccount = mock(SocialAccount.class);
        given(kakaoAccount.getProvider()).willReturn("kakao");
        given(kakaoAccount.isFirst()).willReturn(false);
        given(existingMember.getSocialAccounts()).willReturn(List.of(kakaoAccount));
        given(existingMember.getSocialAccount("kakao")).willReturn(kakaoAccount);

        TokenPairResponse tokenPair = TokenPairResponse.of(1L, "access-token", "refresh-token");
        given(jwtTokenProvider.generateTokenPair(1L, MemberRole.USER, "kakao")).willReturn(tokenPair);

        // when
        SocialLoginResponse response = socialLoginService.socialLogin("kakao", request);

        // then
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.socialTemporaryToken()).isNull();
    }

    @Test
    @DisplayName("인증한 번호의 주인이 다른 회원이면 번호를 채우지 않고 애플 계정을 그 회원에 합친 뒤 그 회원의 토큰을 돌려준다")
    void 번호_주인이_다른_회원이면_애플_계정을_합치고_그_회원의_토큰을_돌려준다() {
        // given
        AppleSignUpRequest request = new AppleSignUpRequest("temp-token");
        TemporaryMember temporaryMember = TemporaryMember.createTemporaryMember("기존회원", "01000000002");
        given(temporaryMemberUtil.getTemporaryMember("temp-token")).willReturn(temporaryMember);
        Member appleMember = appleMember(1L, null);
        given(memberUtil.getCurrentMember()).willReturn(appleMember);
        Member owner = Member.createMember(MemberType.GUARDIAN, "기존회원", "01000000002");
        ReflectionTestUtils.setField(owner, "id", 9L);
        given(memberRepository.findTop2ByPhoneNumber("01000000002")).willReturn(List.of(owner));
        given(appleAccountMergeService.merge(appleMember, owner, "01000000002"))
                .willReturn(TokenPairResponse.of(9L, "owner-access", "owner-refresh"));

        // when
        ApplePhoneNumberResponse response = socialLoginService.updatePhoneNumberIfAppleSignUp(request);

        // then
        assertThat(response.merged()).isTrue();
        assertThat(response.memberId()).isEqualTo(9L);
        assertThat(response.accessToken()).isEqualTo("owner-access");
        assertThat(response.refreshToken()).isEqualTo("owner-refresh");
        verify(memberRepository, never()).fillPhoneNumberIfEmpty(any(), any());
        verify(temporaryMemberUtil).deleteTemporaryMember(temporaryMember.getId());
    }

    @Test
    @DisplayName("인증한 번호를 쓰는 회원이 둘 이상이면 합칠 대상을 정할 수 없어 PHONE_NUMBER_SHARED_BY_MEMBERS 예외가 발생한다")
    void 번호를_쓰는_회원이_둘_이상이면_합치지_않고_거부한다() {
        // given
        AppleSignUpRequest request = new AppleSignUpRequest("temp-token");
        TemporaryMember temporaryMember = TemporaryMember.createTemporaryMember("기존회원", "01000000002");
        given(temporaryMemberUtil.getTemporaryMember("temp-token")).willReturn(temporaryMember);
        given(memberUtil.getCurrentMember()).willReturn(appleMember(1L, null));
        Member first = Member.createMember(MemberType.GUARDIAN, "기존회원", "01000000002");
        Member second = Member.createMember(MemberType.GUARDIAN, "기존회원", "01000000002");
        given(memberRepository.findTop2ByPhoneNumber("01000000002")).willReturn(List.of(first, second));

        // when & then
        assertThatThrownBy(() -> socialLoginService.updatePhoneNumberIfAppleSignUp(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PHONE_NUMBER_SHARED_BY_MEMBERS);
        verifyNoInteractions(appleAccountMergeService);
        verify(memberRepository, never()).fillPhoneNumberIfEmpty(any(), any());
        verify(temporaryMemberUtil, never()).deleteTemporaryMember(any());
    }

    @Test
    @DisplayName("소셜 신규 계정의 전화번호를 쓰는 회원이 둘 이상이면 임의 회원에 연결하거나 새 회원을 만들지 않고 PHONE_NUMBER_SHARED_BY_MEMBERS 예외가 발생한다")
    void 소셜_신규_계정의_전화번호를_쓰는_회원이_둘_이상이면_연결을_거부한다() {
        // given
        SocialLoginRequest request = mock(SocialLoginRequest.class);

        SocialLoginStrategy strategy = mock(SocialLoginStrategy.class);
        given(strategyFactory.getStrategy("kakao")).willReturn(strategy);
        given(strategy.getSupportedProvider()).willReturn(OAuthProvider.KAKAO);

        SocialClientResponse socialResponse = mock(SocialClientResponse.class);
        given(socialResponse.oauthId()).willReturn("oauth-new");
        given(strategy.getUserInfo(request)).willReturn(socialResponse);
        given(strategy.enrichWithRefreshToken(socialResponse, request)).willReturn(socialResponse);
        given(strategy.processUserInfo(socialResponse, request))
                .willReturn(UserInfo.of("카카오유저", "kakao@test.com", "01000000001"));

        given(memberRepository.findMemberIdByProviderAndOauthId("kakao", "oauth-new")).willReturn(Optional.empty());
        given(memberRepository.findTop2ByPhoneNumber("01000000001")).willReturn(List.of(
                Member.createMember(MemberType.GUARDIAN, "보호자", "01000000001"),
                Member.createMember(MemberType.SENIOR, "시니어", "01000000001")));

        // when & then
        assertThatThrownBy(() -> socialLoginService.socialLogin("kakao", request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PHONE_NUMBER_SHARED_BY_MEMBERS);
        verify(memberRepository, never()).save(any(Member.class));
    }

    private static Member appleMember(Long id, String phoneNumber) {
        Member member = Member.createMember(MemberType.GUARDIAN, "애플유저", phoneNumber);
        ReflectionTestUtils.setField(member, "id", id);
        member.getSocialAccounts().add(
                SocialAccount.createSocialAccount("apple@apple.com", "APPLE", "apple-oauth-id", null, member));
        return member;
    }
}
