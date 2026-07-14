package com.widyu.auth.application.guardian.oauth.strategy.naver;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.widyu.auth.application.guardian.oauth.strategy.UserInfo;
import com.widyu.auth.dto.request.SocialLoginRequest;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

@DisplayName("NaverLoginStrategy 예외 처리 단위 테스트")
class NaverLoginStrategyTest {

    private final NaverLoginStrategy strategy = new NaverLoginStrategy(RestClient.builder().build());

    @Test
    @DisplayName("액세스 토큰이 비어 있으면 OAUTH_ACCESS_TOKEN_IS_BLANK 예외를 던진다")
    void 액세스_토큰이_비어_있으면_예외가_발생한다() {
        assertThatThrownBy(() -> strategy.validateLoginRequest(SocialLoginRequest.of(" ")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OAUTH_ACCESS_TOKEN_IS_BLANK)
                .hasMessageContaining("OAuth 액세스 토큰이 비어 있습니다.");
    }

    @Test
    @DisplayName("이메일이 없으면 SOCIAL_EMAIL_NOT_PROVIDED 예외를 던진다")
    void 이메일이_없으면_예외가_발생한다() {
        assertThatThrownBy(() -> strategy.validateUserInfo(UserInfo.of("홍길동", null, "01011112222")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SOCIAL_EMAIL_NOT_PROVIDED)
                .hasMessageContaining("소셜 로그인 제공자가 이메일을 제공하지 않습니다.");
    }

    @Test
    @DisplayName("이름이 없으면 SOCIAL_NAME_NOT_PROVIDED 예외를 던진다")
    void 이름이_없으면_예외가_발생한다() {
        assertThatThrownBy(() -> strategy.validateUserInfo(UserInfo.of(null, "naver@test.com", "01011112222")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SOCIAL_NAME_NOT_PROVIDED)
                .hasMessageContaining("소셜 로그인 제공자가 이름을 제공하지 않습니다.");
    }
}
