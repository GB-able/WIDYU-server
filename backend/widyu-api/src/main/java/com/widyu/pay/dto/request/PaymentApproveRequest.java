package com.widyu.pay.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PaymentApproveRequest(
        @NotBlank(message = "주문 ID는 필수입니다.")
        String orderId,

        @NotBlank(message = "결제 키는 필수입니다.")
        String paymentKey
) {
}
