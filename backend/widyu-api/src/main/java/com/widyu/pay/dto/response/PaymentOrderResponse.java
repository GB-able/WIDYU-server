package com.widyu.pay.dto.response;

import com.widyu.pay.PaymentOrder;
import com.widyu.pay.PaymentOrderStatus;
import java.time.ZonedDateTime;

public record PaymentOrderResponse(
        String orderId,
        String packageId,
        String orderName,
        int amount,
        int pointAmount,
        PaymentOrderStatus status,
        ZonedDateTime expiresAt
) {
    public static PaymentOrderResponse from(PaymentOrder paymentOrder) {
        return new PaymentOrderResponse(
                paymentOrder.getOrderId(),
                paymentOrder.getPackageId(),
                paymentOrder.getOrderName(),
                paymentOrder.getAmount(),
                paymentOrder.getPointAmount(),
                paymentOrder.getStatus(),
                paymentOrder.getExpiresAt()
        );
    }
}
