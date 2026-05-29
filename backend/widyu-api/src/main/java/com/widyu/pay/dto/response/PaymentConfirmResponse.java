package com.widyu.pay.dto.response;

import com.widyu.pay.Payment;
import com.widyu.pay.PaymentCancel;
import com.widyu.pay.PaymentCard;
import com.widyu.pay.PaymentEasyPay;
import com.widyu.pay.PaymentStatus;
import com.widyu.pay.PaymentTransfer;
import com.widyu.pay.PaymentVirtualAccount;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Getter
@NoArgsConstructor
public class PaymentConfirmResponse {

    private String mId;
    private String lastTransactionKey;
    private String paymentKey;
    private String orderId;
    private String orderName;
    private int amount;
    private int taxExemptionAmount;
    private PaymentStatus status;
    private ZonedDateTime requestedAt;
    private ZonedDateTime approvedAt;
    private int canceledAmount;
    private int canceledPointAmount;
    private int remainingAmount;
    private boolean useEscrow;
    private boolean cultureExpense;
    private List<CancelHistory> cancellations;

    // 세부 결제 수단
    private Card card;
    private EasyPay easyPay;
    private Transfer transfer;
    private VirtualAccount virtualAccount;

    // ---------------------- inner classes ----------------------
    @Getter
    @NoArgsConstructor
    public static class Card {
        private String issuerCode;
        private String acquirerCode;
        private String number;
        private int installmentPlanMonths;
        private boolean isInterestFree;
        private String approveNo;
        private String cardType;
    }

    @Getter
    @NoArgsConstructor
    public static class EasyPay {
        private String provider;
        private int amount;
    }

    @Getter
    @NoArgsConstructor
    public static class Transfer {
        private String bankCode;
        private String settlementStatus;
    }

    @Getter
    @NoArgsConstructor
    public static class VirtualAccount {
        private String accountNumber;
        private String bankCode;
        private ZonedDateTime dueDate;
        private boolean expired;
    }

    @Getter
    @NoArgsConstructor
    public static class CancelHistory {
        private int cancelAmount;
        private int cancelPointAmount;
        private String cancelReason;
        private Long requestedByMemberId;
        private ZonedDateTime canceledAt;
    }

    // ---------------------- 정적 팩토리 메서드 ----------------------
    public static PaymentConfirmResponse from(Payment payment) {
        PaymentConfirmResponse response = new PaymentConfirmResponse();

        response.paymentKey = payment.getPaymentKey();
        response.orderId = payment.getOrderId();
        response.orderName = payment.getOrderName();
        response.amount = payment.getAmount();
        response.status = payment.getStatus();
        response.requestedAt = payment.getRequestedAt();
        response.approvedAt = payment.getApprovedAt();
        response.canceledAmount = payment.getCanceledAmount();
        response.canceledPointAmount = payment.getCanceledPointAmount();
        response.remainingAmount = payment.getRemainingAmount();
        response.cultureExpense = payment.isCultureExpense();
        response.cancellations = payment.getCancellations().stream()
                .map(PaymentConfirmResponse::mapCancelHistory)
                .toList();

        // 카드 결제 매핑
        if (payment.getCard() != null) {
            PaymentCard card = payment.getCard();
            Card cardDto = new Card();
            cardDto.issuerCode = card.getIssuerCode();
            cardDto.acquirerCode = card.getAcquirerCode();
            cardDto.number = card.getNumber();
            cardDto.installmentPlanMonths = card.getInstallmentPlanMonths();
            cardDto.isInterestFree = card.isInterestFree();
            cardDto.approveNo = card.getApproveNo();
            cardDto.cardType = card.getCardType();

            response.card = cardDto;
        }

        // 간편결제 매핑
        if (payment.getEasyPay() != null) {
            PaymentEasyPay easyPay = payment.getEasyPay();
            EasyPay easyPayDto = new EasyPay();
            easyPayDto.provider = easyPay.getProvider();
            easyPayDto.amount = easyPay.getAmount();
            response.easyPay = easyPayDto;
        }

        // 계좌이체 매핑
        if (payment.getTransfer() != null) {
            PaymentTransfer transfer = payment.getTransfer();
            Transfer transferDto = new Transfer();
            transferDto.bankCode = transfer.getBankCode();
            transferDto.settlementStatus = transfer.getSettlementStatus();
            response.transfer = transferDto;
        }

        // 가상계좌 매핑
        if (payment.getVirtualAccount() != null) {
            PaymentVirtualAccount va = payment.getVirtualAccount();
            VirtualAccount vaDto = new VirtualAccount();
            vaDto.accountNumber = va.getAccountNumber();
            vaDto.bankCode = va.getBankCode();
            vaDto.dueDate = va.getDueDate();
            vaDto.expired = va.isExpired();
            response.virtualAccount = vaDto;
        }

        return response;
    }

    private static CancelHistory mapCancelHistory(PaymentCancel paymentCancel) {
        CancelHistory cancelHistory = new CancelHistory();
        cancelHistory.cancelAmount = paymentCancel.getCancelAmount();
        cancelHistory.cancelPointAmount = paymentCancel.getCancelPointAmount();
        cancelHistory.cancelReason = paymentCancel.getCancelReason();
        cancelHistory.requestedByMemberId = paymentCancel.getRequestedByMemberId();
        cancelHistory.canceledAt = paymentCancel.getCanceledAt();
        return cancelHistory;
    }

}
