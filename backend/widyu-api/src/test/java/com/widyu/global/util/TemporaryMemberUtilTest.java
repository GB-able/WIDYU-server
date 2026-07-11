package com.widyu.global.util;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.widyu.auth.dto.TemporaryTokenDto;
import com.widyu.auth.repository.TemporaryMemberRepository;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.member.MemberRole;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;

@ExtendWith(MockitoExtension.class)
@DisplayName("TemporaryMemberUtil 예외 처리 단위 테스트")
class TemporaryMemberUtilTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private TemporaryMemberRepository temporaryMemberRepository;
    @Mock private HttpServletRequest request;

    @InjectMocks
    private TemporaryMemberUtil temporaryMemberUtil;

    @Test
    @DisplayName("임시 토큰 헤더가 없으면 UNAUTHORIZED 예외를 던진다")
    void 임시_토큰_헤더가_없으면_예외가_발생한다() {
        // given
        given(request.getHeader(HttpHeaders.AUTHORIZATION)).willReturn(null);

        // when & then
        assertThatThrownBy(() -> temporaryMemberUtil.getTemporaryMemberFromRequest(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED)
                .hasMessageContaining("인증이 필요합니다.");
    }

    @Test
    @DisplayName("JWT 파싱 결과가 없으면 UNAUTHORIZED 예외를 던진다")
    void JWT_파싱_결과가_없으면_예외가_발생한다() {
        // given
        given(request.getHeader(HttpHeaders.AUTHORIZATION)).willReturn("Bearer token");
        given(jwtUtil.parseTemporaryToken("token")).willReturn(null);

        // when & then
        assertThatThrownBy(() -> temporaryMemberUtil.getTemporaryMemberFromRequest(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED)
                .hasMessageContaining("인증이 필요합니다.");
    }

    @Test
    @DisplayName("임시 토큰 역할이 아니면 UNAUTHORIZED 예외를 던진다")
    void 임시_토큰_역할이_아니면_예외가_발생한다() {
        // given
        given(request.getHeader(HttpHeaders.AUTHORIZATION)).willReturn("Bearer token");
        given(jwtUtil.parseTemporaryToken("token"))
                .willReturn(new TemporaryTokenDto("temp-id", MemberRole.USER, "token", 1800L));

        // when & then
        assertThatThrownBy(() -> temporaryMemberUtil.getTemporaryMemberFromRequest(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED)
                .hasMessageContaining("인증이 필요합니다.");
    }

    @Test
    @DisplayName("임시 회원 저장 정보가 없으면 MEMBER_NOT_FOUND 예외를 던진다")
    void 임시_회원_저장_정보가_없으면_예외가_발생한다() {
        // given
        given(request.getHeader(HttpHeaders.AUTHORIZATION)).willReturn("Bearer token");
        given(jwtUtil.parseTemporaryToken("token"))
                .willReturn(new TemporaryTokenDto("temp-id", MemberRole.TEMPORARY, "token", 1800L));
        given(temporaryMemberRepository.findById("temp-id")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> temporaryMemberUtil.getTemporaryMemberFromRequest(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND)
                .hasMessageContaining("회원을 찾을 수 없습니다.");
    }
}
