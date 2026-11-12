package com.widyu.member;

import com.widyu.global.entity.BaseTimeEntity;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 어르신이 앱에서 남긴 탈퇴 요청. 운영자가 확인해 계정을 처리하고 처리 완료·취소를 기록한다.
 * 서버는 계정을 바꾸지 않고 요청과 처리 사실만 남긴다. → LLD-0137
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "member_withdrawal_request", indexes = {
        @Index(name = "idx_member_withdrawal_request_member", columnList = "member_id,status"),
        @Index(name = "idx_member_withdrawal_request_status", columnList = "status,id")
})
public class MemberWithdrawalRequest extends BaseTimeEntity {

    public static final int PROCESSING_DAYS = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private MemberWithdrawalRequestStatus status;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "processed_by_admin_id")
    private Long processedByAdminId;

    private MemberWithdrawalRequest(Long memberId, LocalDateTime requestedAt) {
        this.memberId = memberId;
        this.status = MemberWithdrawalRequestStatus.REQUESTED;
        this.requestedAt = requestedAt;
    }

    public static MemberWithdrawalRequest request(Long memberId, LocalDateTime now) {
        return new MemberWithdrawalRequest(memberId, now);
    }

    public LocalDateTime getDueAt() {
        return requestedAt.plusDays(PROCESSING_DAYS);
    }

    public void complete(Long adminId, LocalDateTime now) {
        close(MemberWithdrawalRequestStatus.PROCESSED, adminId, now);
    }

    public void cancel(Long adminId, LocalDateTime now) {
        close(MemberWithdrawalRequestStatus.CANCELLED, adminId, now);
    }

    private void close(MemberWithdrawalRequestStatus to, Long adminId, LocalDateTime now) {
        if (status != MemberWithdrawalRequestStatus.REQUESTED) {
            throw new BusinessException(ErrorCode.MEMBER_WITHDRAWAL_REQUEST_CLOSED);
        }
        this.status = to;
        this.processedAt = now;
        this.processedByAdminId = adminId;
    }
}
