package com.widyu.auth.application.guardian.oauth.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.widyu.auth.OAuthProvider;
import com.widyu.global.error.BusinessException;
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
        given(kakaoStrategy.getSupportedProvider()).willReturn(OAuthProvider.KAKAO);
        given(naverStrategy.getSupportedProvider()).willReturn(OAuthProvider.NAVER);
        given(appleStrategy.getSupportedProvider()).willReturn(OAuthProvider.APPLE);

        factory = new SocialLoginStrategyFactory(List.of(kakaoStrategy, naverStrategy, appleStrategy));
    }

    @Test
    @DisplayName("KAKAO provider로 조회 시 카카오 전략을 반환한다")
    void KAKAO_provider로_조회_시_카카오_전략을_반환한다() {
        // when
        SocialLoginStrategy strategy = factory.getStrategy(OAuthProvider.KAKAO);

        // then
        assertThat(strategy).isEqualTo(kakaoStrategy);
    }

    @Test
    @DisplayName("NAVER provider로 조회 시 네이버 전략을 반환한다")
    void NAVER_provider로_조회_시_네이버_전략을_반환한다() {
        // when
        SocialLoginStrategy strategy = factory.getStrategy(OAuthProvider.NAVER);

        // then
        assertThat(strategy).isEqualTo(naverStrategy);
    }

    @Test
    @DisplayName("APPLE provider로 조회 시 애플 전략을 반환한다")
    void APPLE_provider로_조회_시_애플_전략을_반환한다() {
        // when
        SocialLoginStrategy strategy = factory.getStrategy(OAuthProvider.APPLE);

        // then
        assertThat(strategy).isEqualTo(appleStrategy);
    }

    @Test
    @DisplayName("문자열 'kakao'로 조회 시 카카오 전략을 반환한다")
    void 문자열_kakao로_조회_시_카카오_전략을_반환한다() {
        // when
        SocialLoginStrategy strategy = factory.getStrategy("kakao");

        // then
        assertThat(strategy).isEqualTo(kakaoStrategy);
    }

    @Test
    @DisplayName("문자열 'naver'로 조회 시 네이버 전략을 반환한다")
    void 문자열_naver로_조회_시_네이버_전략을_반환한다() {
        // when
        SocialLoginStrategy strategy = factory.getStrategy("naver");

        // then
        assertThat(strategy).isEqualTo(naverStrategy);
    }

    @Test
    @DisplayName("문자열 'apple'로 조회 시 애플 전략을 반환한다")
    void 문자열_apple로_조회_시_애플_전략을_반환한다() {
        // when
        SocialLoginStrategy strategy = factory.getStrategy("apple");

        // then
        assertThat(strategy).isEqualTo(appleStrategy);
    }

    @Test
    @DisplayName("지원하지 않는 provider로 조회 시 BusinessException을 던진다")
    void 지원하지_않는_provider로_조회_시_예외가_발생한다() {
        // when & then
        assertThatThrownBy(() -> factory.getStrategy("github"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("팩토리 초기화 시 세 가지 전략이 모두 등록된다")
    void 팩토리_초기화_시_세_가지_전략이_모두_등록된다() {
        // when & then
        assertThat(factory.getStrategy(OAuthProvider.KAKAO)).isNotNull();
        assertThat(factory.getStrategy(OAuthProvider.NAVER)).isNotNull();
        assertThat(factory.getStrategy(OAuthProvider.APPLE)).isNotNull();
    }
}
