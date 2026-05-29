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
@Table(name = "payment_order")
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

    public void markPaid() {
        this.status = PaymentOrderStatus.PAID;
    }

    public void markCanceled() {
        this.status = PaymentOrderStatus.CANCELED;
    }

    public void markExpired() {
        this.status = PaymentOrderStatus.EXPIRED;
    }
}
