package com.widyu.pay.dto.request;

// Toss 취소 API 전용 본문 — 내부 멱등 키(idempotencyKey)는 PG에 전송하지 않는다 (ADR-0012)
public record PaymentGatewayCancelRequest(
        String cancelReason,
        Integer cancelAmount
) {
    public static PaymentGatewayCancelRequest from(CancelRequest cancelRequest) {
        return new PaymentGatewayCancelRequest(cancelRequest.cancelReason(), cancelRequest.cancelAmount());
    }
}
