package com.widyu.admin.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.PointHistory;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.PointHistoryRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminPointGrantService 예외 처리 단위 테스트")
class AdminPointGrantServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private SeniorProfileRepository seniorProfileRepository;
    @Mock private PointHistoryRepository pointHistoryRepository;

    @InjectMocks
    private AdminPointGrantService adminPointGrantService;

    @Test
    @DisplayName("존재하지 않는 회원에게 포인트 지급 시 MEMBER_NOT_FOUND 예외를 던진다")
    void 존재하지_않는_회원_포인트_지급_시_예외가_발생한다() {
        // given
        given(memberRepository.findById(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> adminPointGrantService.grant(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEMBER_NOT_FOUND)
                .hasMessageContaining("회원을 찾을 수 없습니다.");
        then(pointHistoryRepository).should(never()).save(any(PointHistory.class));
    }

    @Test
    @DisplayName("보호자에게 포인트 지급 시 FORBIDDEN 예외를 던진다")
    void 보호자_포인트_지급_시_예외가_발생한다() {
        // given
        Member guardian = Member.createMember(MemberType.GUARDIAN, "보호자", "01011112222");
        given(memberRepository.findById(1L)).willReturn(Optional.of(guardian));

        // when & then
        assertThatThrownBy(() -> adminPointGrantService.grant(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN)
                .hasMessageContaining("포인트는 시니어 회원에게만 지급할 수 있습니다.");
        then(pointHistoryRepository).should(never()).save(any(PointHistory.class));
    }
}
