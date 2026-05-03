package com.widyu.auth.application.callback;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.widyu.global.properties.CallbackProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuthCallbackService 단위 테스트")
class OAuthCallbackServiceTest {

    @Mock private CallbackProperties callbackProperties;

    @InjectMocks
    private OAuthCallbackService oAuthCallbackService;

    @Test
    @DisplayName("애플 OAuth 성공 콜백 시 code와 id_token이 포함된 인텐트 URL로 리다이렉트된다")
    void 애플_OAuth_성공_콜백_시_code와_id_token이_포함된_URL로_리다이렉트된다() throws IOException {
        // given
        HttpServletResponse httpServletResponse = org.mockito.Mockito.mock(HttpServletResponse.class);
        CallbackProperties.Schemes schemes = new CallbackProperties.Schemes("widyu-apple", "widyu-google", "widyu-kakao");
        given(callbackProperties.packageName()).willReturn("com.widyu.app");
        given(callbackProperties.schemes()).willReturn(schemes);

        // when
        oAuthCallbackService.generateAppleCallbackIntentUrl("auth-code-123", "id-token-456", null, httpServletResponse);

        // then
        verify(httpServletResponse).sendRedirect(argThat(url ->
                url.contains("code=auth-code-123") &&
                url.contains("id_token=id-token-456") &&
                url.contains("com.widyu.app") &&
                url.contains("widyu-apple") &&
                url.startsWith("intent://callback?")
        ));
    }

    @Test
    @DisplayName("애플 OAuth 에러 콜백 시 error가 포함된 인텐트 URL로 리다이렉트된다")
    void 애플_OAuth_에러_콜백_시_error가_포함된_URL로_리다이렉트된다() throws IOException {
        // given
        HttpServletResponse httpServletResponse = org.mockito.Mockito.mock(HttpServletResponse.class);
        CallbackProperties.Schemes schemes = new CallbackProperties.Schemes("widyu-apple", "widyu-google", "widyu-kakao");
        given(callbackProperties.packageName()).willReturn("com.widyu.app");
        given(callbackProperties.schemes()).willReturn(schemes);

        // when
        oAuthCallbackService.generateAppleCallbackIntentUrl(null, null, "access_denied", httpServletResponse);

        // then
        verify(httpServletResponse).sendRedirect(argThat(url ->
                url.contains("error=access_denied") &&
                !url.contains("code=") &&
                url.contains("com.widyu.app")
        ));
    }

    @Test
    @DisplayName("애플 OAuth 성공 콜백 시 code가 null이면 빈 문자열로 처리된다")
    void 애플_OAuth_성공_콜백_시_code가_null이면_빈_문자열로_처리된다() throws IOException {
        // given
        HttpServletResponse httpServletResponse = org.mockito.Mockito.mock(HttpServletResponse.class);
        CallbackProperties.Schemes schemes = new CallbackProperties.Schemes("widyu-apple", "widyu-google", "widyu-kakao");
        given(callbackProperties.packageName()).willReturn("com.widyu.app");
        given(callbackProperties.schemes()).willReturn(schemes);

        // when
        oAuthCallbackService.generateAppleCallbackIntentUrl(null, "id-token-456", null, httpServletResponse);

        // then
        verify(httpServletResponse).sendRedirect(argThat(url ->
                url.contains("code=") &&
                url.contains("id_token=id-token-456")
        ));
    }

    @Test
    @DisplayName("애플 OAuth 성공 콜백 시 idToken이 null이면 빈 문자열로 처리된다")
    void 애플_OAuth_성공_콜백_시_idToken이_null이면_빈_문자열로_처리된다() throws IOException {
        // given
        HttpServletResponse httpServletResponse = org.mockito.Mockito.mock(HttpServletResponse.class);
        CallbackProperties.Schemes schemes = new CallbackProperties.Schemes("widyu-apple", "widyu-google", "widyu-kakao");
        given(callbackProperties.packageName()).willReturn("com.widyu.app");
        given(callbackProperties.schemes()).willReturn(schemes);

        // when
        oAuthCallbackService.generateAppleCallbackIntentUrl("auth-code-123", null, null, httpServletResponse);

        // then
        verify(httpServletResponse).sendRedirect(argThat(url ->
                url.contains("code=auth-code-123") &&
                url.contains("id_token=")
        ));
    }
}
