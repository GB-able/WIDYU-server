package com.widyu.pay.application;

import com.widyu.pay.dto.request.CancelRequest;
import java.time.ZonedDateTime;

public record PaymentCancellationCommand(
        Long cancellationId,
        String paymentKey,
        CancelRequest request,
        String pgIdempotencyKey,
        int expectedCanceledAmount,
        ZonedDateTime requestedAt,
        String lastErrorCode
) {

    public PaymentCancellationCommand(Long cancellationId, String paymentKey, CancelRequest request,
                                      String pgIdempotencyKey, int expectedCanceledAmount, ZonedDateTime requestedAt) {
        this(cancellationId, paymentKey, request, pgIdempotencyKey, expectedCanceledAmount, requestedAt, null);
    }
}
