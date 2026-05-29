package com.widyu.pay.dto.mapper;

import com.widyu.pay.Payment;
import com.widyu.pay.PaymentCard;
import com.widyu.pay.PaymentEasyPay;
import com.widyu.pay.PaymentOrder;
import com.widyu.pay.PaymentStatus;
import com.widyu.pay.PaymentTransfer;
import com.widyu.pay.PaymentVirtualAccount;
import com.widyu.pay.dto.response.PaymentConfirmResponse;
import com.widyu.member.Member;

public class PaymentMapper {

    public static Payment toEntity(PaymentConfirmResponse dto, Member member, PaymentOrder paymentOrder) {
        Payment payment = Payment.builder()
                .member(member)
                .paymentOrder(paymentOrder)
                .paymentKey(dto.getPaymentKey())
                .orderId(dto.getOrderId())
                .orderName(dto.getOrderName())
                .amount(dto.getAmount())
                .canceledAmount(0)
                .canceledPointAmount(0)
                .status(dto.getStatus() != null ? dto.getStatus() : PaymentStatus.DONE)
                .requestedAt(dto.getRequestedAt())
                .approvedAt(dto.getApprovedAt())
                .cultureExpense(dto.isCultureExpense())
                .build();

        // 카드 결제
        if (dto.getCard() != null) {
            PaymentCard card = PaymentCard.builder()
                    .issuerCode(dto.getCard().getIssuerCode())
                    .acquirerCode(dto.getCard().getAcquirerCode())
                    .number(dto.getCard().getNumber())
                    .installmentPlanMonths(dto.getCard().getInstallmentPlanMonths())
                    .isInterestFree(dto.getCard().isInterestFree())
                    .approveNo(dto.getCard().getApproveNo())
                    .cardType(dto.getCard().getCardType())
                    .build();
            payment.assignCard(card);
        }

        // 간편결제
        if (dto.getEasyPay() != null) {
            PaymentEasyPay easyPay = PaymentEasyPay.builder()
                    .provider(dto.getEasyPay().getProvider())
                    .amount(dto.getEasyPay().getAmount())
                    .build();
            payment.assignEasyPay(easyPay);
        }

        // 계좌이체
        if (dto.getTransfer() != null) {
            PaymentTransfer transfer = PaymentTransfer.builder()
                    .bankCode(dto.getTransfer().getBankCode())
                    .settlementStatus(dto.getTransfer().getSettlementStatus())
                    .build();
            payment.assignTransfer(transfer);
        }

        // 가상계좌
        if (dto.getVirtualAccount() != null) {
            PaymentVirtualAccount va = PaymentVirtualAccount.builder()
                    .accountNumber(dto.getVirtualAccount().getAccountNumber())
                    .bankCode(dto.getVirtualAccount().getBankCode())
                    .dueDate(dto.getVirtualAccount().getDueDate())
                    .expired(dto.getVirtualAccount().isExpired())
                    .build();
            payment.assignVirtualAccount(va);
        }

        return payment;
    }
}
