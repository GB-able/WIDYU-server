package com.widyu.pay.infrastructure;

public class PaymentGatewayException extends RuntimeException {

    private final int status;
    private final String errorCode;

    public PaymentGatewayException(int status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public int getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
