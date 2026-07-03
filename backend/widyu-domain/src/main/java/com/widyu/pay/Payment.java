package com.widyu.pay;

import com.widyu.member.Member;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String paymentKey;

    @Column(nullable = false)
    private String orderId;
    private String orderName;
    private int amount;
    private int canceledAmount;
    private int canceledPointAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    private ZonedDateTime requestedAt;
    private ZonedDateTime approvedAt;

    private String cancelReason;
    private ZonedDateTime canceledAt;

    private boolean cultureExpense;

    @OneToOne(mappedBy = "payment", cascade = CascadeType.ALL)
    private PaymentCard card;

    @OneToOne(mappedBy = "payment", cascade = CascadeType.ALL)
    private PaymentVirtualAccount virtualAccount;

    @OneToOne(mappedBy = "payment", cascade = CascadeType.ALL)
    private PaymentTransfer transfer;

    @OneToOne(mappedBy = "payment", cascade = CascadeType.ALL)
    private PaymentEasyPay easyPay;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_order_id", unique = true)
    private PaymentOrder paymentOrder;

    @Builder.Default
    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentCancel> cancellations = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    public void assignCard(PaymentCard card) {
        this.card = card;
        if (card != null) {
            card.assignPayment(this);
        }
    }

    public void assignEasyPay(PaymentEasyPay easyPay) {
        this.easyPay = easyPay;
        if (easyPay != null) {
            easyPay.assignPayment(this);
        }
    }

    public void assignTransfer(PaymentTransfer transfer) {
        this.transfer = transfer;
        if (transfer != null) {
            transfer.assignPayment(this);
        }
    }

    public void assignVirtualAccount(PaymentVirtualAccount virtualAccount) {
        this.virtualAccount = virtualAccount;
        if (virtualAccount != null) {
            virtualAccount.assignPayment(this);
        }
    }

    public void assignPaymentOrder(PaymentOrder paymentOrder) {
        this.paymentOrder = paymentOrder;
    }

    public void addCancellation(PaymentCancel cancellation) {
        this.cancellations.add(cancellation);
        cancellation.assignPayment(this);
    }

    public boolean isOwnedBy(Long memberId) {
        return member != null && Objects.equals(member.getId(), memberId);
    }

    public boolean isCanceled() {
        return this.status == PaymentStatus.CANCELED;
    }

    public boolean matches(String orderId, int amount) {
        return Objects.equals(this.orderId, orderId) && this.amount == amount;
    }

    public int getRemainingAmount() {
        return this.amount - this.canceledAmount;
    }

    public void cancel(int cancelAmount, int cancelPointAmount, String reason, ZonedDateTime canceledAt) {
        this.canceledAmount += cancelAmount;
        this.canceledPointAmount += cancelPointAmount;
        this.cancelReason = reason;
        this.canceledAt = canceledAt;
        this.status = this.canceledAmount >= this.amount ? PaymentStatus.CANCELED : PaymentStatus.PARTIAL_CANCELED;
    }
}
