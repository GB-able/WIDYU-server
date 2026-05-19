package com.widyu.admin.dto.response;

import com.widyu.pay.Payment;
import com.widyu.pay.PaymentStatus;
import java.time.ZonedDateTime;

public record AdminPaymentResponse(
        Long id,
        Long memberId,
        String memberName,
        String orderName,
        int amount,
        PaymentStatus status,
        String paymentMethod,
        ZonedDateTime approvedAt,
        ZonedDateTime canceledAt
) {
    public static AdminPaymentResponse from(Payment payment) {
        String method = resolveMethod(payment);
        return new AdminPaymentResponse(
                payment.getId(),
                payment.getMember().getId(),
                payment.getMember().getName(),
                payment.getOrderName(),
                payment.getAmount(),
                payment.getStatus(),
                method,
                payment.getApprovedAt(),
                payment.getCanceledAt()
        );
    }

    private static String resolveMethod(Payment p) {
        if (p.getCard() != null) return "카드";
        if (p.getEasyPay() != null) return "간편결제";
        if (p.getTransfer() != null) return "계좌이체";
        if (p.getVirtualAccount() != null) return "가상계좌";
        return "기타";
    }
}
