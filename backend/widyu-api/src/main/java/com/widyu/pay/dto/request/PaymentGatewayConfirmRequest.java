package com.widyu.pay.dto.request;

public record PaymentGatewayConfirmRequest(
        String orderId,
        int amount,
        String paymentKey
) {
    public static PaymentGatewayConfirmRequest of(String orderId, int amount, String paymentKey) {
        return new PaymentGatewayConfirmRequest(orderId, amount, paymentKey);
    }
}
