package com.widyu.pay.application;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.application.SeniorProfileService;
import com.widyu.pay.Payment;
import com.widyu.pay.PaymentCancel;
import com.widyu.pay.PaymentOrder;
import com.widyu.pay.PaymentStatus;
import com.widyu.pay.PointChargePackage;
import com.widyu.pay.config.PaymentClient;
import com.widyu.pay.dto.mapper.PaymentMapper;
import com.widyu.pay.dto.request.CancelRequest;
import com.widyu.pay.dto.request.PaymentApproveRequest;
import com.widyu.pay.dto.request.PaymentGatewayConfirmRequest;
import com.widyu.pay.dto.request.PaymentOrderCreateRequest;
import com.widyu.pay.dto.response.PaymentConfirmResponse;
import com.widyu.pay.dto.response.PaymentConfirmResponses;
import com.widyu.pay.dto.response.PaymentPackageResponse;
import com.widyu.pay.dto.response.PaymentOrderResponse;
import com.widyu.pay.repository.PaymentOrderRepository;
import com.widyu.pay.repository.PaymentRepository;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private static final int ORDER_ID_MAX_ATTEMPTS = 5;
    private static final int ORDER_EXPIRATION_MINUTES = 15;

    private final PaymentClient paymentClient;
    private final PaymentRepository paymentRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final MemberUtil memberUtil;
    private final SeniorProfileService seniorProfileService;

    public List<PaymentPackageResponse> getPackages() {
        return java.util.Arrays.stream(PointChargePackage.values())
                .map(PaymentPackageResponse::from)
                .toList();
    }

    @Transactional
    public PaymentOrderResponse createOrder(PaymentOrderCreateRequest request) {
        Member currentMember = memberUtil.getCurrentMember();
        validateSeniorMember(currentMember);
        PointChargePackage paymentPackage = resolvePackage(request.packageId());
        PaymentOrder paymentOrder = PaymentOrder.create(
                generateOrderId(),
                currentMember,
                paymentPackage.getOrderName(),
                paymentPackage.getId(),
                paymentPackage.getAmount(),
                paymentPackage.getPointAmount(),
                ZonedDateTime.now().plusMinutes(ORDER_EXPIRATION_MINUTES)
        );
        paymentOrderRepository.save(paymentOrder);
        return PaymentOrderResponse.from(paymentOrder);
    }

    @Transactional
    public PaymentConfirmResponse confirmPayment(PaymentApproveRequest request) {
        Member currentMember = memberUtil.getCurrentMember();
        validateSeniorMember(currentMember);
        PaymentOrder paymentOrder = paymentOrderRepository.findByOrderId(request.orderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND, "주문 정보를 찾을 수 없습니다."));

        validatePaymentOrderOwnership(paymentOrder, currentMember.getId());
        validatePaymentOrderState(paymentOrder);

        Payment existingOrderPayment = paymentRepository.findByOrderId(request.orderId()).orElse(null);
        if (existingOrderPayment != null) {
            validatePaymentOwnership(existingOrderPayment, currentMember.getId());
            if (!Objects.equals(existingOrderPayment.getPaymentKey(), request.paymentKey())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "이미 결제 완료된 주문입니다.");
            }
            return PaymentConfirmResponse.from(existingOrderPayment);
        }

        Payment existingPayment = paymentRepository.findByPaymentKey(request.paymentKey()).orElse(null);
        if (existingPayment != null) {
            validatePaymentOwnership(existingPayment, currentMember.getId());
            validateExistingPaymentMatchesOrder(existingPayment, paymentOrder);
            return PaymentConfirmResponse.from(existingPayment);
        }

        PaymentGatewayConfirmRequest gatewayRequest = new PaymentGatewayConfirmRequest(
                paymentOrder.getOrderId(),
                paymentOrder.getAmount(),
                request.paymentKey()
        );
        PaymentConfirmResponse rawResponse = paymentClient.confirmPayment(gatewayRequest);
        validateConfirmResponse(rawResponse, paymentOrder, request.paymentKey());

        Payment payment = PaymentMapper.toEntity(rawResponse, currentMember, paymentOrder);

        try {
            paymentRepository.save(payment);
            paymentOrder.markPaid();
            PointChargePackage paymentPackage = resolvePackage(paymentOrder.getPackageId());
            // 이 메서드는 이미 트랜잭션 안이므로 addPointsToMember의 @RetryOnPointConflict는 여기서는 동작하지 않는다.
            // 결제 확인은 외부 PG 호출을 재실행하면 안 되므로 서버 재시도 대신, 포인트 @Version 충돌 시
            // 트랜잭션 전체를 롤백하고 409(POINT_CONCURRENT_UPDATE)로 응답한다. (결제는 orderId/paymentKey로 멱등하여 클라이언트 재시도 안전)
            seniorProfileService.addPointsToMember(
                    currentMember.getId(),
                    (long) paymentPackage.getPointAmount(),
                    paymentPackage.getOrderName()
            );
        } catch (DataIntegrityViolationException e) {
            Payment duplicatedPayment = paymentRepository.findByPaymentKey(request.paymentKey())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_FAILED, "중복 결제 저장 처리에 실패했습니다."));
            validatePaymentOwnership(duplicatedPayment, currentMember.getId());
            validateExistingPaymentMatchesOrder(duplicatedPayment, paymentOrder);
            return PaymentConfirmResponse.from(duplicatedPayment);
        }

        return PaymentConfirmResponse.from(payment);
    }

    @Transactional
    public PaymentConfirmResponse cancelPayment(String paymentKey, CancelRequest cancelRequest) {
        Payment payment = paymentRepository.findByPaymentKey(paymentKey)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        Member currentMember = memberUtil.getCurrentMember();

        validatePaymentOwnership(payment, currentMember.getId());

        if (payment.isCanceled()) {
            return PaymentConfirmResponse.from(payment);
        }

        CancelRequest effectiveRequest = sanitizeCancelRequest(payment, cancelRequest);
        int refundPointAmount = calculateRefundPointAmount(payment, effectiveRequest.cancelAmount());
        validateRefundPointBalance(currentMember, refundPointAmount);
        PaymentConfirmResponse response = paymentClient.cancelPayment(paymentKey, effectiveRequest);
        validateCancelResponse(response, paymentKey, effectiveRequest.cancelAmount());

        ZonedDateTime canceledAt = ZonedDateTime.now();
        PaymentCancel paymentCancel = PaymentCancel.create(
                payment,
                effectiveRequest.cancelAmount(),
                refundPointAmount,
                effectiveRequest.cancelReason(),
                currentMember.getId(),
                canceledAt
        );
        payment.addCancellation(paymentCancel);
        payment.cancel(effectiveRequest.cancelAmount(), refundPointAmount, effectiveRequest.cancelReason(), canceledAt);
        // confirmPayment와 동일: 상위 트랜잭션 안이라 재시도가 동작하지 않으며, PG 취소 재호출을 피하기 위해
        // 포인트 @Version 충돌 시 롤백 후 409로 응답한다. (취소는 isCanceled 가드로 멱등)
        seniorProfileService.deductPointsFromMember(
                currentMember.getId(),
                (long) refundPointAmount,
                effectiveRequest.cancelReason()
        );

        if (payment.getPaymentOrder() != null && payment.getStatus() == PaymentStatus.CANCELED) {
            payment.getPaymentOrder().markCanceled();
        }

        return PaymentConfirmResponse.from(payment);
    }

    public PaymentConfirmResponses getPaymentsByUser() {
        Member currentMember = memberUtil.getCurrentMember();
        List<Payment> payments = paymentRepository.findByMemberId(currentMember.getId());

        if (payments.isEmpty()) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_FOUND);
        }

        return PaymentConfirmResponses.from(payments);
    }

    private void validatePaymentOwnership(Payment payment, Long memberId) {
        if (!payment.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인 결제만 접근할 수 있습니다.");
        }
    }

    private void validateSeniorMember(Member member) {
        if (member.getType() != MemberType.SENIOR || member.getSeniorProfile() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "시니어 회원만 포인트를 충전할 수 있습니다.");
        }
    }

    private void validatePaymentOrderOwnership(PaymentOrder paymentOrder, Long memberId) {
        if (!paymentOrder.isOwnedBy(memberId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인 주문만 결제할 수 있습니다.");
        }
    }

    private void validatePaymentOrderState(PaymentOrder paymentOrder) {
        if (paymentOrder.isExpired()) {
            paymentOrder.markExpired();
            throw new BusinessException(ErrorCode.BAD_REQUEST, "만료된 주문입니다.");
        }
        if (!paymentOrder.isCreated()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "이미 처리된 주문입니다.");
        }
    }

    private void validateExistingPaymentMatchesOrder(Payment payment, PaymentOrder paymentOrder) {
        if (!payment.matches(paymentOrder.getOrderId(), paymentOrder.getAmount())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "기존 결제 요청 정보와 일치하지 않습니다.");
        }
    }

    private void validateConfirmResponse(PaymentConfirmResponse response, PaymentOrder paymentOrder, String paymentKey) {
        if (!Objects.equals(response.getPaymentKey(), paymentKey)
                || !Objects.equals(response.getOrderId(), paymentOrder.getOrderId())
                || response.getAmount() != paymentOrder.getAmount()) {
            throw new BusinessException(ErrorCode.PAYMENT_FAILED, "PG 응답과 요청 정보가 일치하지 않습니다.");
        }
    }

    private void validateCancelResponse(PaymentConfirmResponse response, String paymentKey, int cancelAmount) {
        if (!Objects.equals(response.getPaymentKey(), paymentKey)) {
            throw new BusinessException(ErrorCode.PAYMENT_FAILED, "취소 응답의 결제 키가 일치하지 않습니다.");
        }
        if (cancelAmount <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "취소 금액은 0보다 커야 합니다.");
        }
    }

    private CancelRequest sanitizeCancelRequest(Payment payment, CancelRequest cancelRequest) {
        String reason = cancelRequest != null ? cancelRequest.cancelReason() : null;
        if (reason == null || reason.isBlank()) {
            reason = "사용자 요청";
        }

        int remainingAmount = payment.getRemainingAmount();
        Integer requestedCancelAmount = cancelRequest != null ? cancelRequest.cancelAmount() : null;
        int cancelAmount = requestedCancelAmount != null ? requestedCancelAmount : remainingAmount;

        if (cancelAmount <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "취소 금액은 0보다 커야 합니다.");
        }
        if (cancelAmount > remainingAmount) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "남은 결제 금액보다 크게 취소할 수 없습니다.");
        }

        return new CancelRequest(reason, cancelAmount);
    }

    private int calculateRefundPointAmount(Payment payment, int cancelAmount) {
        PaymentOrder paymentOrder = payment.getPaymentOrder();
        if (paymentOrder == null) {
            return cancelAmount;
        }

        int nextCanceledAmount = payment.getCanceledAmount() + cancelAmount;
        int targetCanceledPoints = (int) (((long) paymentOrder.getPointAmount() * nextCanceledAmount) / payment.getAmount());
        return targetCanceledPoints - payment.getCanceledPointAmount();
    }

    private void validateRefundPointBalance(Member currentMember, int refundPointAmount) {
        if (refundPointAmount <= 0) {
            return;
        }
        if (currentMember.getSeniorProfile() == null || !currentMember.getSeniorProfile().hasEnoughPoints((long) refundPointAmount)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "결제 취소에 필요한 포인트가 부족합니다.");
        }
    }

    private String generateOrderId() {
        for (int attempt = 0; attempt < ORDER_ID_MAX_ATTEMPTS; attempt++) {
            String candidate = "order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            if (!paymentOrderRepository.existsByOrderId(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "주문 ID 생성에 실패했습니다.");
    }

    private PointChargePackage resolvePackage(String packageId) {
        try {
            return PointChargePackage.fromId(packageId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, e.getMessage());
        }
    }
}
