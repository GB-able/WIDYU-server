package com.widyu.member.dto.response;

import com.widyu.member.MemberWithdrawalRequest;
import com.widyu.member.MemberWithdrawalRequestStatus;
import java.time.LocalDateTime;

/** 탈퇴 요청 응답. 어르신과 관리자가 같은 형식을 받는다. 이름·전화번호는 담지 않는다(LLD-0137). */
public record MemberWithdrawalRequestResponse(
        Long requestId,
        Long memberId,
        MemberWithdrawalRequestStatus status,
        LocalDateTime requestedAt,
        LocalDateTime dueAt,
        LocalDateTime processedAt,
        Long processedByAdminId
) {
    public static MemberWithdrawalRequestResponse from(MemberWithdrawalRequest request) {
        return new MemberWithdrawalRequestResponse(
                request.getId(),
                request.getMemberId(),
                request.getStatus(),
                request.getRequestedAt(),
                request.getDueAt(),
                request.getProcessedAt(),
                request.getProcessedByAdminId()
        );
    }
}
