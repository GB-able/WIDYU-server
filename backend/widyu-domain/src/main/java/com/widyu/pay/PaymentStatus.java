package com.widyu.pay;

public enum PaymentStatus {
    READY,
    IN_PROGRESS,
    WAITING_FOR_DEPOSIT,
    DONE,
    PARTIAL_CANCELED,
    CANCELED,
    ABORTED,
    EXPIRED
}
