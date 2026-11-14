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
        socialLoginService.updatePhoneNumberIfAppleSignUp(request);

        // then
        verify(memberRepository).fillPhoneNumberIfEmpty(1L, "01099999999");
        verify(temporaryMemberUtil).deleteTemporaryMember(temporaryMember.getId());
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
    @DisplayName("다른 회원이 쓰는 번호로 애플 회원 번호를 보강하면 ALREADY_REGISTERED_PHONE_NUMBER 예외가 발생하고 번호를 채우지 않는다")
    void 다른_회원이_쓰는_번호로_애플_회원_번호를_보강하면_거부한다() {
        // given
        AppleSignUpRequest request = new AppleSignUpRequest("temp-token");
        TemporaryMember temporaryMember = TemporaryMember.createTemporaryMember("애플유저", "01000000002");
        given(temporaryMemberUtil.getTemporaryMember("temp-token")).willReturn(temporaryMember);
        given(memberUtil.getCurrentMember()).willReturn(appleMember(1L, null));
        given(memberRepository.existsByPhoneNumberAndIdNot("01000000002", 1L)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> socialLoginService.updatePhoneNumberIfAppleSignUp(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_REGISTERED_PHONE_NUMBER);
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

    @Test
    @DisplayName("전화번호로 다른 계정이 있는 기존 회원을 찾으면 연결 토큰 응답에 개인정보 없이 로그인 수단 목록만 담는다")
    void 연결_토큰_응답에_개인정보_없이_로그인_수단_목록만_담는다() {
        // given
        SocialLoginRequest request = mock(SocialLoginRequest.class);
        givenNaverUser(request,
                SocialClientResponse.of("naver-new", "attacker@naver.com", "공격자", "01012345678", null, true));

        Member victim = memberWithSocialAccount(1L, "피해자", "01012345678", "victim@kakao.com", "kakao");
        given(memberRepository.findMemberIdByProviderAndOauthId("naver", "naver-new")).willReturn(Optional.empty());
        given(memberRepository.findTop2ByPhoneNumber("01012345678")).willReturn(List.of(victim));
        given(memberRepository.findWithAllAccountsById(1L)).willReturn(Optional.of(victim));
        given(socialTemporaryTokenService.createSocialTemporaryToken(1L, "naver", "naver-new", "attacker@naver.com"))
                .willReturn("social-temp-token");

        // when
        SocialLoginResponse response = socialLoginService.socialLogin("naver", request);

        // then
        assertThat(response.accessToken()).isNull();
        assertThat(response.memberId()).isNull();
        assertThat(response.socialTemporaryToken()).isEqualTo("social-temp-token");
        assertThat(response.profile().providers()).containsExactly("kakao");
        assertThat(response.profile().name()).isEmpty();
        assertThat(response.profile().phoneNumber()).isEmpty();
        assertThat(response.profile().email()).isEmpty();
    }

    @Test
    @DisplayName("공급자가 이메일을 인증하지 않았으면 같은 이메일의 기존 회원을 찾지 않고 새 회원으로 가입시킨다")
    void 미인증_이메일이면_기존_회원을_찾지_않고_새_회원으로_가입시킨다() {
        // given
        SocialLoginRequest request = mock(SocialLoginRequest.class);
        givenKakaoUser(request,
                SocialClientResponse.of("kakao-new", "victim@example.com", "공격자", null, null, false));
        given(memberRepository.findMemberIdByProviderAndOauthId("kakao", "kakao-new")).willReturn(Optional.empty());
        given(memberRepository.save(any(Member.class))).willAnswer(invocation -> {
            Member saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 2L);
            return saved;
        });
        given(jwtTokenProvider.generateTokenPair(2L, MemberRole.USER, "kakao"))
                .willReturn(TokenPairResponse.of(2L, "new-access", "new-refresh"));

        // when
        SocialLoginResponse response = socialLoginService.socialLogin("kakao", request);

        // then
        assertThat(response.memberId()).isEqualTo(2L);
        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.socialTemporaryToken()).isNull();
        verify(memberRepository, never()).findDistinctTop2BySocialAccounts_Email(any());
        verify(socialTemporaryTokenService, never()).createSocialTemporaryToken(any(), any(), any(), any());
    }

    @Test
    @DisplayName("공급자가 인증한 이메일이 다른 계정이 있는 기존 회원과 같으면 연결 토큰을 발급한다")
    void 인증된_이메일이_기존_회원과_같으면_연결_토큰을_발급한다() {
        // given
        SocialLoginRequest request = mock(SocialLoginRequest.class);
        givenKakaoUser(request,
                SocialClientResponse.of("kakao-new", "owner@example.com", "주인", null, null, true));
        Member owner = memberWithSocialAccount(1L, "주인", "01011112222", "owner@example.com", "APPLE");
        given(memberRepository.findMemberIdByProviderAndOauthId("kakao", "kakao-new")).willReturn(Optional.empty());
        given(memberRepository.findDistinctTop2BySocialAccounts_Email("owner@example.com")).willReturn(List.of(owner));
        given(memberRepository.findWithAllAccountsById(1L)).willReturn(Optional.of(owner));
        given(socialTemporaryTokenService.createSocialTemporaryToken(1L, "kakao", "kakao-new", "owner@example.com"))
                .willReturn("social-temp-token");

        // when
        SocialLoginResponse response = socialLoginService.socialLogin("kakao", request);

        // then
        assertThat(response.socialTemporaryToken()).isEqualTo("social-temp-token");
        assertThat(response.profile().providers()).containsExactly("APPLE");
        verify(memberRepository, never()).save(any(Member.class));
    }

    @Test
    @DisplayName("인증된 이메일을 쓰는 회원이 둘 이상이면 어느 회원에도 연결 토큰을 발급하지 않고 새 회원으로 가입시킨다")
    void 같은_이메일의_회원이_둘_이상이면_연결하지_않고_새_회원으로_가입시킨다() {
        // given
        SocialLoginRequest request = mock(SocialLoginRequest.class);
        givenKakaoUser(request,
                SocialClientResponse.of("kakao-new", "shared@example.com", "사용자", null, null, true));
        given(memberRepository.findMemberIdByProviderAndOauthId("kakao", "kakao-new")).willReturn(Optional.empty());
        given(memberRepository.findDistinctTop2BySocialAccounts_Email("shared@example.com")).willReturn(List.of(
                memberWithSocialAccount(1L, "회원1", null, "shared@example.com", "APPLE"),
                memberWithSocialAccount(2L, "회원2", null, "shared@example.com", "naver")));
        given(memberRepository.save(any(Member.class))).willAnswer(invocation -> {
            Member saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 3L);
            return saved;
        });
        given(jwtTokenProvider.generateTokenPair(3L, MemberRole.USER, "kakao"))
                .willReturn(TokenPairResponse.of(3L, "new-access", "new-refresh"));

        // when
        SocialLoginResponse response = socialLoginService.socialLogin("kakao", request);

        // then
        assertThat(response.memberId()).isEqualTo(3L);
        assertThat(response.socialTemporaryToken()).isNull();
        verify(socialTemporaryTokenService, never()).createSocialTemporaryToken(any(), any(), any(), any());
    }

    private void givenKakaoUser(SocialLoginRequest request, SocialClientResponse socialResponse) {
        givenSocialUser("kakao", OAuthProvider.KAKAO, request, socialResponse);
    }

    private void givenNaverUser(SocialLoginRequest request, SocialClientResponse socialResponse) {
        givenSocialUser("naver", OAuthProvider.NAVER, request, socialResponse);
    }

    private void givenSocialUser(String providerName, OAuthProvider provider,
                                 SocialLoginRequest request, SocialClientResponse socialResponse) {
        SocialLoginStrategy strategy = mock(SocialLoginStrategy.class);
        given(strategyFactory.getStrategy(providerName)).willReturn(strategy);
        given(strategy.getSupportedProvider()).willReturn(provider);
        given(strategy.getUserInfo(request)).willReturn(socialResponse);
        given(strategy.enrichWithRefreshToken(socialResponse, request)).willReturn(socialResponse);
        given(strategy.processUserInfo(socialResponse, request)).willReturn(
                UserInfo.of(socialResponse.name(), socialResponse.email(), socialResponse.phoneNumber()));
    }

    private static Member memberWithSocialAccount(Long id, String name, String phoneNumber, String email, String provider) {
        Member member = Member.createMember(MemberType.GUARDIAN, name, phoneNumber);
        ReflectionTestUtils.setField(member, "id", id);
        member.getSocialAccounts().add(
                SocialAccount.createSocialAccount(email, provider, provider + "-oauth-id", null, member));
        return member;
    }

    private static Member appleMember(Long id, String phoneNumber) {
        Member member = Member.createMember(MemberType.GUARDIAN, "애플유저", phoneNumber);
        ReflectionTestUtils.setField(member, "id", id);
        member.getSocialAccounts().add(
                SocialAccount.createSocialAccount("apple@apple.com", "APPLE", "apple-oauth-id", null, member));
        return member;
    }
}
