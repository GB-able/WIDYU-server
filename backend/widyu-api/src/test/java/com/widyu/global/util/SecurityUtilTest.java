package com.widyu.global.util;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@DisplayName("SecurityUtil 예외 처리 단위 테스트")
class SecurityUtilTest {

    private final SecurityUtil securityUtil = new SecurityUtil();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증 정보가 없으면 현재 회원 ID 조회 시 UNAUTHORIZED 예외를 던진다")
    void 인증_정보가_없으면_회원_ID_조회_시_예외가_발생한다() {
        assertThatThrownBy(securityUtil::getCurrentMemberId)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED)
                .hasMessageContaining("인증이 필요합니다.");
    }

    @Test
    @DisplayName("권한 정보가 없으면 현재 회원 권한 조회 시 UNAUTHORIZED 예외를 던진다")
    void 권한_정보가_없으면_회원_권한_조회_시_예외가_발생한다() {
        // given
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("1", null, java.util.List.of()));

        // when & then
        assertThatThrownBy(securityUtil::getCurrentMemberRole)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED)
                .hasMessageContaining("인증이 필요합니다.");
    }
}
