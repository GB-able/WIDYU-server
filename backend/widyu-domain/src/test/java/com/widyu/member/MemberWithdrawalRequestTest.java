package com.widyu.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MemberWithdrawalRequestTest {

    private static final LocalDateTime REQUESTED_AT = LocalDateTime.of(2026, 11, 12, 10, 0);

    @Test
    @DisplayName("탈퇴를 요청하면 처리 대기 상태이고 처리 기한은 요청 시각 10일 뒤다")
    void 탈퇴를_요청하면_처리_대기_상태이고_처리_기한은_10일_뒤다() {
        // when
        MemberWithdrawalRequest request = MemberWithdrawalRequest.request(7L, REQUESTED_AT);

        // then
        assertThat(request.getStatus()).isEqualTo(MemberWithdrawalRequestStatus.REQUESTED);
        assertThat(request.getDueAt()).isEqualTo(LocalDateTime.of(2026, 11, 22, 10, 0));
        assertThat(request.getProcessedAt()).isNull();
    }

    @Test
    @DisplayName("열린 요청을 처리 완료하면 처리 시각과 처리 관리자가 남는다")
    void 열린_요청을_처리_완료하면_처리_시각과_관리자가_남는다() {
        // given
        MemberWithdrawalRequest request = MemberWithdrawalRequest.request(7L, REQUESTED_AT);
        LocalDateTime processedAt = REQUESTED_AT.plusDays(3);

        // when
        request.complete(1L, processedAt);

        // then
        assertThat(request.getStatus()).isEqualTo(MemberWithdrawalRequestStatus.PROCESSED);
        assertThat(request.getProcessedAt()).isEqualTo(processedAt);
        assertThat(request.getProcessedByAdminId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("열린 요청을 취소하면 취소 상태와 처리 관리자가 남는다")
    void 열린_요청을_취소하면_취소_상태와_관리자가_남는다() {
        // given
        MemberWithdrawalRequest request = MemberWithdrawalRequest.request(7L, REQUESTED_AT);

        // when
        request.cancel(2L, REQUESTED_AT.plusDays(1));

        // then
        assertThat(request.getStatus()).isEqualTo(MemberWithdrawalRequestStatus.CANCELLED);
        assertThat(request.getProcessedByAdminId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("이미 닫힌 요청을 다시 처리하면 예외가 발생하고 기록은 바뀌지 않는다")
    void 이미_닫힌_요청을_다시_처리하면_예외가_발생한다() {
        // given
        MemberWithdrawalRequest request = MemberWithdrawalRequest.request(7L, REQUESTED_AT);
        request.cancel(2L, REQUESTED_AT.plusDays(1));

        // when & then
        assertThatThrownBy(() -> request.complete(1L, REQUESTED_AT.plusDays(2)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_WITHDRAWAL_REQUEST_CLOSED);
        assertThat(request.getStatus()).isEqualTo(MemberWithdrawalRequestStatus.CANCELLED);
        assertThat(request.getProcessedByAdminId()).isEqualTo(2L);
    }
}
