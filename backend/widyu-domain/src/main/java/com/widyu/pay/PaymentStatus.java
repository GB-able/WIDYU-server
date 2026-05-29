package com.widyu.pay;

public enum PaymentStatus {
    READY,      // 결제 준비
    DONE,   // 결제 승인 완료
    PARTIAL_CANCELED,
    CANCELED;   // 결제 취소
}
