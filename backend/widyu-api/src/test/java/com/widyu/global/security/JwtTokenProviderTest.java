package com.widyu.global.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.widyu.auth.dto.RefreshTokenDto;
import com.widyu.auth.repository.RefreshTokenRepository;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtTokenProvider 예외 처리 단위 테스트")
class JwtTokenProviderTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("만료된 액세스 토큰은 EXPIRED_ACCESS_TOKEN 예외를 던진다")
    void 만료된_액세스_토큰은_예외가_발생한다() {
        // given
        given(jwtUtil.parseAccessToken("expired-token"))
                .willThrow(new ExpiredJwtException(null, null, "expired"));

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.retrieveAccessToken("expired-token"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.EXPIRED_ACCESS_TOKEN)
                .hasMessageContaining("액세스 토큰이 만료되었습니다.");
    }

    @Test
    @DisplayName("파싱 결과가 없는 액세스 토큰은 INVALID_ACCESS_TOKEN 예외를 던진다")
    void 파싱_결과가_없는_액세스_토큰은_예외가_발생한다() {
        // given
        given(jwtUtil.parseAccessToken("invalid-token")).willReturn(null);

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.retrieveAccessToken("invalid-token"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_ACCESS_TOKEN)
                .hasMessageContaining("유효하지 않은 액세스 토큰입니다.");
    }

    @Test
    @DisplayName("저장소에 없는 리프레시 토큰은 INVALID_REFRESH_TOKEN 예외를 던진다")
    void 저장소에_없는_리프레시_토큰은_예외가_발생한다() {
        // given
        given(jwtUtil.parseRefreshToken("refresh-token"))
                .willReturn(new RefreshTokenDto(1L, "refresh-token", 3600L));
        given(refreshTokenRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.retrieveRefreshToken("refresh-token"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_REFRESH_TOKEN)
                .hasMessageContaining("리프레시 토큰이 유효하지 않습니다.");
    }

    @Test
    @DisplayName("파싱 결과가 없는 소셜 임시 토큰은 INVALID_TEMPORARY_TOKEN 예외를 던진다")
    void 파싱_결과가_없는_소셜_임시_토큰은_예외가_발생한다() {
        // given
        given(jwtUtil.parseSocialTemporaryToken("invalid-social-token")).willReturn(null);

        // when & then
        assertThatThrownBy(() -> jwtTokenProvider.retrieveSocialTemporaryToken("invalid-social-token"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TEMPORARY_TOKEN)
                .hasMessageContaining("유효하지 않은 임시 토큰입니다.");
    }
}
