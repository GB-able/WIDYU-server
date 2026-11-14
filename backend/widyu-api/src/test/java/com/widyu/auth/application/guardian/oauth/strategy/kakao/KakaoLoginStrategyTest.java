package com.widyu.auth.application.guardian.oauth.strategy.kakao;

import static com.widyu.global.constant.SecurityConstant.KAKAO_USER_ME_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.widyu.auth.application.guardian.oauth.strategy.UserInfo;
import com.widyu.auth.dto.request.SocialLoginRequest;
import com.widyu.auth.dto.response.SocialClientResponse;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.properties.KakaoProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@DisplayName("KakaoLoginStrategy 예외 처리 단위 테스트")
class KakaoLoginStrategyTest {

    private final KakaoLoginStrategy strategy = new KakaoLoginStrategy(
            RestClient.builder().build(),
            new KakaoProperties("client-id", "redirect-uri", "admin-key")
    );

    @Test
    @DisplayName("액세스 토큰이 비어 있으면 OAUTH_ACCESS_TOKEN_IS_BLANK 예외를 던진다")
    void 액세스_토큰이_비어_있으면_예외가_발생한다() {
        assertThatThrownBy(() -> strategy.validateLoginRequest(SocialLoginRequest.of(null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OAUTH_ACCESS_TOKEN_IS_BLANK)
                .hasMessageContaining("OAuth 액세스 토큰이 비어 있습니다.");
    }

    @Test
    @DisplayName("이메일이 없으면 SOCIAL_EMAIL_NOT_PROVIDED 예외를 던진다")
    void 이메일이_없으면_예외가_발생한다() {
        assertThatThrownBy(() -> strategy.validateUserInfo(UserInfo.of("홍길동", " ", "01011112222")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SOCIAL_EMAIL_NOT_PROVIDED)
                .hasMessageContaining("소셜 로그인 제공자가 이메일을 제공하지 않습니다.");
    }

    @Test
    @DisplayName("이름이 없으면 SOCIAL_NAME_NOT_PROVIDED 예외를 던진다")
    void 이름이_없으면_예외가_발생한다() {
        assertThatThrownBy(() -> strategy.validateUserInfo(UserInfo.of(" ", "kakao@test.com", "01011112222")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SOCIAL_NAME_NOT_PROVIDED)
                .hasMessageContaining("소셜 로그인 제공자가 이름을 제공하지 않습니다.");
    }

    @ParameterizedTest(name = "is_email_verified={0}")
    @CsvSource({"true, true", "false, false", "null, false"})
    @DisplayName("카카오 is_email_verified가 true일 때만 인증된 이메일로 본다")
    void 카카오_is_email_verified가_true일_때만_인증된_이메일로_본다(String isEmailVerifiedJson, boolean expected) {
        // given
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer kakaoServer = MockRestServiceServer.bindTo(builder).build();
        KakaoLoginStrategy strategyWithServer = new KakaoLoginStrategy(
                builder.build(),
                new KakaoProperties("client-id", "redirect-uri", "admin-key")
        );
        kakaoServer.expect(requestTo(KAKAO_USER_ME_URL))
                .andRespond(withSuccess("{\"id\":1,\"kakao_account\":{\"email\":\"owner@example.com\","
                        + "\"is_email_verified\":" + isEmailVerifiedJson + ",\"name\":\"홍길동\","
                        + "\"phone_number\":\"+82 10-1111-2222\"}}", MediaType.APPLICATION_JSON));

        // when
        SocialClientResponse response = strategyWithServer.getUserInfo(SocialLoginRequest.of("kakao-access-token"));

        // then
        assertThat(response.email()).isEqualTo("owner@example.com");
        assertThat(response.emailVerified()).isEqualTo(expected);
        kakaoServer.verify();
    }
}
