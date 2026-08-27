package com.widyu.pay;

import com.widyu.global.entity.BaseTimeEntity;
import com.widyu.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "payment_order",
        indexes = @Index(
                name = "idx_payment_order_approval_recovery",
                columnList = "status, approval_next_retry_at"
        )
)
public class PaymentOrder extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private String orderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "order_name", nullable = false)
    private String orderName;

    @Column(name = "package_id", nullable = false)
    private String packageId;

    @Column(nullable = false)
    private int amount;

    @Column(name = "point_amount", nullable = false)
    private int pointAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentOrderStatus status;

    @Column(name = "expires_at", nullable = false)
    private ZonedDateTime expiresAt;

    @Column(name = "approval_payment_key", length = 200)
    private String approvalPaymentKey;

    @Column(name = "approval_pg_idempotency_key", length = 36)
    private String approvalPgIdempotencyKey;

    @Column(name = "approval_requested_at")
    private ZonedDateTime approvalRequestedAt;

    @Column(name = "approval_retry_count", nullable = false)
    private int approvalRetryCount;

    @Column(name = "approval_next_retry_at")
    private ZonedDateTime approvalNextRetryAt;

    @Column(name = "approval_last_error_code", length = 100)
    private String approvalLastErrorCode;

    @Column(name = "approval_recovery_stopped_at")
    private ZonedDateTime approvalRecoveryStoppedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private PaymentOrder(String orderId, Member member, String orderName, String packageId, int amount, int pointAmount,
                         PaymentOrderStatus status, ZonedDateTime expiresAt) {
        this.orderId = orderId;
        this.member = member;
        this.orderName = orderName;
        this.packageId = packageId;
        this.amount = amount;
        this.pointAmount = pointAmount;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    public static PaymentOrder create(String orderId, Member member, String orderName, String packageId, int amount, int pointAmount,
                                      ZonedDateTime expiresAt) {
        return PaymentOrder.builder()
                .orderId(orderId)
                .member(member)
                .orderName(orderName)
                .packageId(packageId)
                .amount(amount)
                .pointAmount(pointAmount)
                .status(PaymentOrderStatus.CREATED)
                .expiresAt(expiresAt)
                .build();
    }

    public boolean isOwnedBy(Long memberId) {
        return member != null && Objects.equals(member.getId(), memberId);
    }

    public boolean isExpired() {
        return expiresAt.isBefore(ZonedDateTime.now());
    }

    public boolean isCreated() {
        return status == PaymentOrderStatus.CREATED;
    }

    public boolean isApproving() {
        return status == PaymentOrderStatus.APPROVING;
    }

    public boolean matchesApprovalPaymentKey(String paymentKey) {
        return Objects.equals(approvalPaymentKey, paymentKey);
    }

    public void beginApproval(String paymentKey, String pgIdempotencyKey, ZonedDateTime requestedAt) {
        this.status = PaymentOrderStatus.APPROVING;
        this.approvalPaymentKey = paymentKey;
        this.approvalPgIdempotencyKey = pgIdempotencyKey;
        this.approvalRequestedAt = requestedAt;
        this.approvalRetryCount = 0;
        this.approvalNextRetryAt = requestedAt.plusMinutes(2);
        this.approvalLastErrorCode = null;
        this.approvalRecoveryStoppedAt = null;
    }

    public void markPaid() {
        this.status = PaymentOrderStatus.PAID;
        clearApproval();
    }

    public void markCanceled() {
        this.status = PaymentOrderStatus.CANCELED;
    }

    public void markExpired() {
        this.status = PaymentOrderStatus.EXPIRED;
    }

    public void resetApproval() {
        this.status = PaymentOrderStatus.CREATED;
        clearApproval();
    }

    public void scheduleApprovalRecovery(String errorCode, ZonedDateTime nextRetryAt) {
        this.approvalRetryCount++;
        this.approvalLastErrorCode = errorCode;
        this.approvalNextRetryAt = nextRetryAt;
    }

    public void stopApprovalRecovery(String errorCode, ZonedDateTime stoppedAt) {
        this.approvalLastErrorCode = errorCode;
        this.approvalRecoveryStoppedAt = stoppedAt;
        this.approvalNextRetryAt = null;
    }

    private void clearApproval() {
        this.approvalPaymentKey = null;
        this.approvalPgIdempotencyKey = null;
        this.approvalRequestedAt = null;
        this.approvalRetryCount = 0;
        this.approvalNextRetryAt = null;
        this.approvalLastErrorCode = null;
        this.approvalRecoveryStoppedAt = null;
    }
}
