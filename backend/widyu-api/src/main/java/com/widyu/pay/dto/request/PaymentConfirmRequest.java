package com.widyu.pay.dto.request;

public record PaymentConfirmRequest(
        String orderId,
        int amount,
        String paymentKey
) {

}
