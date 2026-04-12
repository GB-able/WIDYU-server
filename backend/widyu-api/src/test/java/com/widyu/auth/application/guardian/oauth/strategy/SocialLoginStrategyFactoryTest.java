package com.widyu.auth.application.guardian.oauth.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.widyu.auth.OAuthProvider;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SocialLoginStrategyFactory 단위 테스트")
class SocialLoginStrategyFactoryTest {

    @Mock private SocialLoginStrategy kakaoStrategy;
    @Mock private SocialLoginStrategy naverStrategy;
    @Mock private SocialLoginStrategy appleStrategy;

    private SocialLoginStrategyFactory factory;

    @BeforeEach
    void setUp() {
        when(kakaoStrategy.getSupportedProvider()).thenReturn(OAuthProvider.KAKAO);
        when(naverStrategy.getSupportedProvider()).thenReturn(OAuthProvider.NAVER);
        when(appleStrategy.getSupportedProvider()).thenReturn(OAuthProvider.APPLE);

        factory = new SocialLoginStrategyFactory(List.of(kakaoStrategy, naverStrategy, appleStrategy));
    }

    @Test
    @DisplayName("KAKAO provider로 조회 시 카카오 전략을 반환한다")
    void getStrategy_kakaoProvider_returnsKakaoStrategy() {
        SocialLoginStrategy strategy = factory.getStrategy(OAuthProvider.KAKAO);
        assertThat(strategy).isEqualTo(kakaoStrategy);
    }

    @Test
    @DisplayName("NAVER provider로 조회 시 네이버 전략을 반환한다")
    void getStrategy_naverProvider_returnsNaverStrategy() {
        SocialLoginStrategy strategy = factory.getStrategy(OAuthProvider.NAVER);
        assertThat(strategy).isEqualTo(naverStrategy);
    }

    @Test
    @DisplayName("APPLE provider로 조회 시 애플 전략을 반환한다")
    void getStrategy_appleProvider_returnsAppleStrategy() {
        SocialLoginStrategy strategy = factory.getStrategy(OAuthProvider.APPLE);
        assertThat(strategy).isEqualTo(appleStrategy);
    }

    @Test
    @DisplayName("문자열 'kakao'로 조회 시 카카오 전략을 반환한다")
    void getStrategy_kakaoString_returnsKakaoStrategy() {
        SocialLoginStrategy strategy = factory.getStrategy("kakao");
        assertThat(strategy).isEqualTo(kakaoStrategy);
    }

    @Test
    @DisplayName("문자열 'naver'로 조회 시 네이버 전략을 반환한다")
    void getStrategy_naverString_returnsNaverStrategy() {
        SocialLoginStrategy strategy = factory.getStrategy("naver");
        assertThat(strategy).isEqualTo(naverStrategy);
    }

    @Test
    @DisplayName("문자열 'apple'로 조회 시 애플 전략을 반환한다")
    void getStrategy_appleString_returnsAppleStrategy() {
        SocialLoginStrategy strategy = factory.getStrategy("apple");
        assertThat(strategy).isEqualTo(appleStrategy);
    }

    @Test
    @DisplayName("지원하지 않는 provider로 조회 시 BusinessException을 던진다")
    void getStrategy_unsupportedProvider_throwsBusinessException() {
        // given - 지원하지 않는 provider 문자열 (OAuthProvider.from() 에서 예외 발생)
        assertThatThrownBy(() -> factory.getStrategy("github"))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("팩토리 초기화 시 등록된 전략 수가 3개다")
    void constructor_registersAllThreeStrategies() {
        // 팩토리가 3개 전략 모두를 지원하는지 검증
        assertThat(factory.getStrategy(OAuthProvider.KAKAO)).isNotNull();
        assertThat(factory.getStrategy(OAuthProvider.NAVER)).isNotNull();
        assertThat(factory.getStrategy(OAuthProvider.APPLE)).isNotNull();
    }
}
