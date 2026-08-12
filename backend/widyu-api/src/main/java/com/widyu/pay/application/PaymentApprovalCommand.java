package com.widyu.pay.application;

import java.time.ZonedDateTime;

public record PaymentApprovalCommand(
        String orderId,
        String paymentKey,
        int amount,
        String pgIdempotencyKey,
        ZonedDateTime requestedAt,
        String lastErrorCode
) {

    public PaymentApprovalCommand(String orderId, String paymentKey, int amount, String pgIdempotencyKey,
                                  ZonedDateTime requestedAt) {
        this(orderId, paymentKey, amount, pgIdempotencyKey, requestedAt, null);
    }
}
