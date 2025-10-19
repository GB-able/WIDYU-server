package com.widyu.pay.dto.request;


public record CancelRequest(
        String cancelReason,
        Integer cancelAmount
) {
}

