package com.widyu.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.widyu.auth.TemporaryMember;
import com.widyu.auth.dto.TemporaryTokenDto;
import com.widyu.auth.repository.TemporaryMemberRepository;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.security.JwtTokenProvider;
import com.widyu.global.util.JwtUtil;
import com.widyu.member.MemberRole;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TemporaryTokenService 단위 테스트")
class TemporaryTokenServiceTest {

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private TemporaryMemberRepository temporaryMemberRepository;
    @Mock private HttpServletRequest httpServletRequest;

    @InjectMocks
    private TemporaryTokenService temporaryTokenService;

    @Test
    @DisplayName("요청 헤더에 임시 토큰이 있으면 토큰을 반환한다")
    void extractFrom_validHeader_returnsToken() {
        String expectedToken = "temp-token-value";
        try (MockedStatic<JwtUtil> jwtUtilMock = Mockito.mockStatic(JwtUtil.class)) {
            jwtUtilMock.when(() -> JwtUtil.extractTemporaryTokenFromHeader(httpServletRequest))
                    .thenReturn(expectedToken);

            String result = temporaryTokenService.extractFrom(httpServletRequest);

            assertThat(result).isEqualTo(expectedToken);
        }
    }

    @Test
    @DisplayName("요청 헤더에 임시 토큰이 없으면 BusinessException을 던진다")
    void extractFrom_missingHeader_throwsBusinessException() {
        try (MockedStatic<JwtUtil> jwtUtilMock = Mockito.mockStatic(JwtUtil.class)) {
            jwtUtilMock.when(() -> JwtUtil.extractTemporaryTokenFromHeader(httpServletRequest))
                    .thenReturn(null);

            assertThatThrownBy(() -> temporaryTokenService.extractFrom(httpServletRequest))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TEMPORARY_TOKEN);
        }
    }

    @Test
    @DisplayName("유효한 임시 토큰 파싱 시 TemporaryTokenDto를 반환한다")
    void parseAndValidate_validToken_returnsDto() {
        String token = "valid-temp-token";
        TemporaryTokenDto expectedDto = new TemporaryTokenDto(
                "temp-member-id", MemberRole.USER, token, 1800L
        );
        when(jwtTokenProvider.retrieveTemporaryToken(token)).thenReturn(expectedDto);

        TemporaryTokenDto result = temporaryTokenService.parseAndValidate(token);

        assertThat(result).isEqualTo(expectedDto);
    }

    @Test
    @DisplayName("임시 토큰 파싱 결과가 null이면 BusinessException을 던진다")
    void parseAndValidate_nullResult_throwsBusinessException() {
        String token = "invalid-temp-token";
        when(jwtTokenProvider.retrieveTemporaryToken(token)).thenReturn(null);

        assertThatThrownBy(() -> temporaryTokenService.parseAndValidate(token))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TEMPORARY_TOKEN);
    }

    @Test
    @DisplayName("존재하는 임시 회원 ID로 조회 시 임시 회원을 반환한다")
    void loadTemporaryMemberOrThrow_existingMember_returnsMember() {
        String memberId = "temp-member-uuid";
        TemporaryMember tempMember = TemporaryMember.createTemporaryMember("홍길동", "01012345678");
        when(temporaryMemberRepository.findById(memberId)).thenReturn(Optional.of(tempMember));

        TemporaryMember result = temporaryTokenService.loadTemporaryMemberOrThrow(memberId);

        assertThat(result).isEqualTo(tempMember);
    }

    @Test
    @DisplayName("존재하지 않는 임시 회원 ID로 조회 시 BusinessException을 던진다")
    void loadTemporaryMemberOrThrow_notFound_throwsBusinessException() {
        when(temporaryMemberRepository.findById("non-existing-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> temporaryTokenService.loadTemporaryMemberOrThrow("non-existing-id"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("임시 회원 삭제 시 레포지토리의 deleteById를 호출한다")
    void deleteTemporaryMember_callsRepository() {
        String memberId = "temp-member-uuid";

        temporaryTokenService.deleteTemporaryMember(memberId);

        verify(temporaryMemberRepository).deleteById(memberId);
    }
}
