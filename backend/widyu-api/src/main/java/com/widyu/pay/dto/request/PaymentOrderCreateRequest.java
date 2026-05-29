package com.widyu.pay.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PaymentOrderCreateRequest(
        @NotBlank(message = "패키지 ID는 필수입니다.")
        String packageId
) {
}
