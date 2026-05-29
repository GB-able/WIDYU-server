package com.widyu.pay.dto.request;

public record PaymentGatewayConfirmRequest(
        String orderId,
        int amount,
        String paymentKey
) {
}
