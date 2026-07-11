package com.widyu.global.util;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.member.repository.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MemberUtil 예외 처리 단위 테스트")
class MemberUtilTest {

    @Mock private SecurityUtil securityUtil;
    @Mock private MemberRepository memberRepository;

    @InjectMocks
    private MemberUtil memberUtil;

    @Test
    @DisplayName("현재 회원을 찾을 수 없으면 MEMBER_NOT_FOUND 예외를 던진다")
    void 현재_회원을_찾을_수_없으면_예외가_발생한다() {
        // given
        given(securityUtil.getCurrentMemberId()).willReturn(1L);
        given(memberRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(memberUtil::getCurrentMember)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND)
                .hasMessageContaining("회원을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("권한 정보가 없으면 UNAUTHORIZED 예외를 던진다")
    void 권한_정보가_없으면_예외가_발생한다() {
        // given
        given(securityUtil.getCurrentMemberRole()).willReturn(null);

        // when & then
        assertThatThrownBy(memberUtil::getMemberRole)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHORIZED)
                .hasMessageContaining("인증이 필요합니다.");
    }
}
