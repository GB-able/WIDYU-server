package com.widyu.pay.dto.request;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CancelRequest(
        @Size(max = 200, message = "취소 사유는 최대 200자입니다.")
        String cancelReason,

        @Positive(message = "취소 금액은 0보다 커야 합니다.")
        Integer cancelAmount
) {
}
