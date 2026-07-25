package com.widyu.pay.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CancelRequest(
        @Size(max = 200, message = "취소 사유는 최대 200자입니다.")
        String cancelReason,

        @Positive(message = "취소 금액은 0보다 커야 합니다.")
        Integer cancelAmount,

        @Size(max = 100, message = "멱등 키는 최대 100자입니다.")
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String idempotencyKey
) {
    public CancelRequest(String cancelReason, Integer cancelAmount) {
        this(cancelReason, cancelAmount, null);
    }
}
