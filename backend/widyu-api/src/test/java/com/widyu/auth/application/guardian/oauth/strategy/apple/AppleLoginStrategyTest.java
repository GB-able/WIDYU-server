package com.widyu.auth.application.guardian.oauth.strategy.apple;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.widyu.auth.application.guardian.oauth.strategy.UserInfo;
import com.widyu.auth.dto.request.SocialLoginRequest;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.properties.AppleProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

@DisplayName("AppleLoginStrategy 예외 처리 단위 테스트")
class AppleLoginStrategyTest {

    private final AppleLoginStrategy strategy = new AppleLoginStrategy(
            new AppleProperties("ios", "android", "team", "key", "private-key", "redirect-uri"),
            mock(AppleJwtUtils.class),
            RestClient.builder().build(),
            new ObjectMapper()
    );

    @Test
    @DisplayName("인증 코드가 비어 있으면 APPLE_AUTHORIZATION_CODE_IS_BLANK 예외를 던진다")
    void 인증_코드가_비어_있으면_예외가_발생한다() {
        assertThatThrownBy(() -> strategy.validateLoginRequest(SocialLoginRequest.builder().authorizationCode(" ").build()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPLE_AUTHORIZATION_CODE_IS_BLANK)
                .hasMessageContaining("애플 인증 코드가 비어 있습니다.");
    }

    @Test
    @DisplayName("이메일이 없으면 SOCIAL_EMAIL_NOT_PROVIDED 예외를 던진다")
    void 이메일이_없으면_예외가_발생한다() {
        assertThatThrownBy(() -> strategy.validateUserInfo(UserInfo.of("익명의 사용자", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SOCIAL_EMAIL_NOT_PROVIDED)
                .hasMessageContaining("소셜 로그인 제공자가 이메일을 제공하지 않습니다.");
    }
}
