package com.widyu.pay;

import com.widyu.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Column(name = "cancel_point_amount", nullable = false)
    private int cancelPointAmount;

    @Column(name = "cancel_reason", nullable = false)
    private String cancelReason;

    @Column(name = "requested_by_member_id", nullable = false)
    private Long requestedByMemberId;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "canceled_at", nullable = false)
    private ZonedDateTime canceledAt;

    @Builder(access = AccessLevel.PRIVATE)
    private PaymentCancel(Payment payment, int cancelAmount, int cancelPointAmount, String cancelReason,
                          Long requestedByMemberId, String idempotencyKey, ZonedDateTime canceledAt) {
        this.payment = payment;
        this.cancelAmount = cancelAmount;
        this.cancelPointAmount = cancelPointAmount;
        this.cancelReason = cancelReason;
        this.requestedByMemberId = requestedByMemberId;
        this.idempotencyKey = idempotencyKey;
        this.canceledAt = canceledAt;
    }

    public static PaymentCancel create(Payment payment, int cancelAmount, int cancelPointAmount, String cancelReason,
                                       Long requestedByMemberId, String idempotencyKey, ZonedDateTime canceledAt) {
        return PaymentCancel.builder()
                .payment(payment)
                .cancelAmount(cancelAmount)
                .cancelPointAmount(cancelPointAmount)
                .cancelReason(cancelReason)
                .requestedByMemberId(requestedByMemberId)
                .idempotencyKey(idempotencyKey)
                .canceledAt(canceledAt)
                .build();
    }

    public boolean matchesRequest(String cancelReason, int cancelAmount) {
        return this.cancelAmount == cancelAmount && this.cancelReason.equals(cancelReason);
    }

    void assignPayment(Payment payment) {
        this.payment = payment;
    }
}
