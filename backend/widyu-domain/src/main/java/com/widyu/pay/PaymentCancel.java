package com.widyu.pay;

import com.widyu.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.ZonedDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "payment_cancel",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payment_cancel_payment_idempotency_key",
                columnNames = {"payment_id", "idempotency_key"}
        )
)
public class PaymentCancel extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "cancel_amount", nullable = false)
    private int cancelAmount;

    // 원본 요청의 취소 금액 — 전액 취소(금액 생략)는 null로 보존해 동일 멱등 키 재요청 비교에 사용한다
    @Column(name = "requested_cancel_amount")
    private Integer requestedCancelAmount;

    @Column(name = "cancel_point_amount", nullable = false)
    private int cancelPointAmount;

    @Column(name = "cancel_reason", nullable = false)
    private String cancelReason;

    @Column(name = "requested_by_member_id", nullable = false)
    private Long requestedByMemberId;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentCancelStatus status;

    @Column(name = "pg_idempotency_key", nullable = false, unique = true, length = 36)
    private String pgIdempotencyKey;

    @Column(name = "canceled_at")
    private ZonedDateTime canceledAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at")
    private ZonedDateTime nextRetryAt;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "recovery_stopped_at")
    private ZonedDateTime recoveryStoppedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private PaymentCancel(Payment payment, int cancelAmount, Integer requestedCancelAmount, int cancelPointAmount,
                          String cancelReason, Long requestedByMemberId, String idempotencyKey,
                          PaymentCancelStatus status, String pgIdempotencyKey, ZonedDateTime canceledAt) {
        this.payment = payment;
        this.cancelAmount = cancelAmount;
        this.requestedCancelAmount = requestedCancelAmount;
        this.cancelPointAmount = cancelPointAmount;
        this.cancelReason = cancelReason;
        this.requestedByMemberId = requestedByMemberId;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.pgIdempotencyKey = pgIdempotencyKey;
        this.canceledAt = canceledAt;
    }

    public static PaymentCancel createPending(Payment payment, int cancelAmount, Integer requestedCancelAmount,
                                              int cancelPointAmount, String cancelReason,
                                              Long requestedByMemberId, String idempotencyKey, String pgIdempotencyKey) {
        PaymentCancel paymentCancel = PaymentCancel.builder()
                .payment(payment)
                .cancelAmount(cancelAmount)
                .requestedCancelAmount(requestedCancelAmount)
                .cancelPointAmount(cancelPointAmount)
                .cancelReason(cancelReason)
                .requestedByMemberId(requestedByMemberId)
                .idempotencyKey(idempotencyKey)
                .status(PaymentCancelStatus.PENDING)
                .pgIdempotencyKey(pgIdempotencyKey)
                .build();
        paymentCancel.nextRetryAt = ZonedDateTime.now().plusMinutes(2);
        return paymentCancel;
    }

    public boolean isPending() {
        return status == PaymentCancelStatus.PENDING;
    }

    public boolean isRecoveryStopped() {
        return recoveryStoppedAt != null;
    }

    public boolean isAborted() {
        return status == PaymentCancelStatus.ABORTED;
    }

    // PG 미반영이 확인된 채 복구를 포기하는 종결 전이 — 이후 새 취소 요청을 막지 않는다
    public void abort(String errorCode, ZonedDateTime stoppedAt) {
        this.status = PaymentCancelStatus.ABORTED;
        this.lastErrorCode = errorCode;
        this.recoveryStoppedAt = stoppedAt;
        this.nextRetryAt = null;
    }

    public void complete(ZonedDateTime completedAt) {
        this.status = PaymentCancelStatus.COMPLETED;
        this.canceledAt = completedAt;
    }

    public void scheduleRecovery(String errorCode, ZonedDateTime nextRetryAt) {
        this.retryCount++;
        this.lastErrorCode = errorCode;
        this.nextRetryAt = nextRetryAt;
    }

    public void stopRecovery(String errorCode, ZonedDateTime stoppedAt) {
        this.lastErrorCode = errorCode;
        this.recoveryStoppedAt = stoppedAt;
        this.nextRetryAt = null;
    }

    public boolean matchesRequest(String cancelReason, int cancelAmount) {
        return this.cancelAmount == cancelAmount && this.cancelReason.equals(cancelReason);
    }

    void assignPayment(Payment payment) {
        this.payment = payment;
    }
}
