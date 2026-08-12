package com.widyu.pay.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    // Toss 응답 역직렬화 전용 — 공개 API 응답 JSON에는 노출하지 않는다 (LLD-0022 응답 형식 유지)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Integer totalAmount;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Integer balanceAmount;

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
        response.totalAmount = payment.getAmount();
        response.balanceAmount = payment.getRemainingAmount();
        response.status = payment.getStatus();
        response.requestedAt = payment.getRequestedAt();
        response.approvedAt = payment.getApprovedAt();
        response.canceledAmount = payment.getCanceledAmount();
        response.canceledPointAmount = payment.getCanceledPointAmount();
        response.remainingAmount = payment.getRemainingAmount();
        response.cultureExpense = payment.isCultureExpense();
        response.cancellations = payment.getCancellations().stream()
                .filter(paymentCancel -> !paymentCancel.isPending())
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

    // Toss 응답은 결제 금액을 totalAmount로 반환한다 — amount는 내부 생성 응답(from)에서만 채워진다
    @JsonIgnore
    public int getResolvedAmount() {
        if (totalAmount != null) {
            return totalAmount;
        }
        return amount;
    }

    // Toss 응답에는 내부 canceledAmount 필드가 없으므로 totalAmount - balanceAmount로 누적 취소 금액을 계산한다
    @JsonIgnore
    public Integer getGatewayCanceledAmount() {
        if (totalAmount == null || balanceAmount == null) {
            return null;
        }
        return totalAmount - balanceAmount;
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
