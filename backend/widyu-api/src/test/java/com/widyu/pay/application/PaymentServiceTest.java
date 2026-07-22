package com.widyu.pay.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.application.SeniorProfileService;
import com.widyu.pay.Payment;
import com.widyu.pay.PaymentOrder;
import com.widyu.pay.PaymentOrderStatus;
import com.widyu.pay.PaymentStatus;
import com.widyu.pay.PointChargePackage;
import com.widyu.pay.config.PaymentClient;
import com.widyu.pay.dto.request.CancelRequest;
import com.widyu.pay.dto.request.PaymentApproveRequest;
import com.widyu.pay.dto.request.PaymentOrderCreateRequest;
import com.widyu.pay.dto.response.PaymentConfirmResponse;
import com.widyu.pay.dto.response.PaymentPackageResponse;
import com.widyu.pay.dto.response.PaymentOrderResponse;
import com.widyu.pay.repository.PaymentOrderRepository;
import com.widyu.pay.repository.PaymentRepository;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService 단위 테스트")
class PaymentServiceTest {

    @Mock private PaymentClient paymentClient;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentOrderRepository paymentOrderRepository;
    @Mock private MemberUtil memberUtil;
    @Mock private SeniorProfileService seniorProfileService;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    @DisplayName("주문 생성 시 서버가 orderId와 만료 시각을 포함한 주문을 저장한다")
    void 주문_생성() {
        Member member = createMember(1L, "보호자");
        given(memberUtil.getCurrentMember()).willReturn(member);
        given(paymentOrderRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        PaymentOrderResponse response = paymentService.createOrder(new PaymentOrderCreateRequest("POINT_10000"));

        assertThat(response.orderId()).startsWith("order_");
        assertThat(response.packageId()).isEqualTo("POINT_10000");
        assertThat(response.amount()).isEqualTo(10000);
        assertThat(response.pointAmount()).isEqualTo(10000);
        assertThat(response.status()).isEqualTo(PaymentOrderStatus.CREATED);
        assertThat(response.expiresAt()).isAfter(ZonedDateTime.now());
    }

    @Test
    @DisplayName("결제 패키지 목록을 조회하면 서버 카탈로그를 반환한다")
    void 결제_패키지_목록_조회() {
        java.util.List<PaymentPackageResponse> responses = paymentService.getPackages();

        assertThat(responses).isNotEmpty();
        assertThat(responses.get(0).packageId()).isEqualTo(PointChargePackage.POINT_10000.getId());
    }

    @Test
    @DisplayName("이미 저장된 주문 결제면 기존 결제를 반환하고 PG 승인 호출을 생략한다")
    void 중복_결제_승인_요청은_기존_결제를_반환한다() {
        Member member = createMember(1L, "보호자");
        PaymentOrder paymentOrder = createOrder(member, "order_123", 10000, PaymentOrderStatus.CREATED);
        Payment payment = createPayment(member, paymentOrder, "pay_123", "order_123", 10000, PaymentStatus.DONE);
        PaymentApproveRequest request = new PaymentApproveRequest("order_123", "pay_123");

        given(memberUtil.getCurrentMember()).willReturn(member);
        given(paymentOrderRepository.findByOrderId("order_123")).willReturn(Optional.of(paymentOrder));
        given(paymentRepository.findByOrderId("order_123")).willReturn(Optional.of(payment));

        PaymentConfirmResponse response = paymentService.confirmPayment(request);

        assertThat(response.getPaymentKey()).isEqualTo("pay_123");
        verify(paymentClient, never()).confirmPayment(any());
    }

    @Test
    @DisplayName("다른 사용자의 주문을 승인하려 하면 예외가 발생한다")
    void 타인_주문_결제_승인은_예외가_발생한다() {
        Member currentMember = createMember(1L, "보호자");
        Member otherMember = createMember(2L, "다른보호자");
        PaymentOrder paymentOrder = createOrder(otherMember, "order_123", 10000, PaymentOrderStatus.CREATED);
        PaymentApproveRequest request = new PaymentApproveRequest("order_123", "pay_123");

        given(memberUtil.getCurrentMember()).willReturn(currentMember);
        given(paymentOrderRepository.findByOrderId("order_123")).willReturn(Optional.of(paymentOrder));

        assertThatThrownBy(() -> paymentService.confirmPayment(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        verify(paymentClient, never()).confirmPayment(any());
    }

    @Test
    @DisplayName("결제 취소 시 본인 결제면 취소하고 승인 시각은 유지한다")
    void 본인_결제_취소() {
        Member member = createMember(1L, "보호자");
        given(member.getSeniorProfile().hasEnoughPoints(10000L)).willReturn(true);
        PaymentOrder paymentOrder = createOrder(member, "order_123", 10000, PaymentOrderStatus.PAID);
        Payment payment = createPayment(member, paymentOrder, "pay_123", "order_123", 10000, PaymentStatus.DONE);
        ZonedDateTime approvedAt = payment.getApprovedAt();
        PaymentConfirmResponse canceledResponse = createResponse("pay_123", "order_123", 10000, PaymentStatus.CANCELED);

        given(memberUtil.getCurrentMember()).willReturn(member);
        given(paymentRepository.findByPaymentKey("pay_123")).willReturn(Optional.of(payment));
        given(paymentClient.cancelPayment("pay_123", new CancelRequest("사용자 요청", 10000))).willReturn(canceledResponse);

        PaymentConfirmResponse result = paymentService.cancelPayment("pay_123", null);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(payment.getCancelReason()).isEqualTo("사용자 요청");
        assertThat(payment.getCanceledAt()).isNotNull();
        assertThat(payment.getCanceledAmount()).isEqualTo(10000);
        assertThat(payment.getCanceledPointAmount()).isEqualTo(10000);
        assertThat(payment.getRemainingAmount()).isZero();
        assertThat(payment.getApprovedAt()).isEqualTo(approvedAt);
        assertThat(paymentOrder.getStatus()).isEqualTo(PaymentOrderStatus.CANCELED);
        assertThat(result.getCancellations()).hasSize(1);
        verify(seniorProfileService).deductPointsFromMember(member.getId(), 10000L, "사용자 요청");
    }

    @Test
    @DisplayName("다른 사용자의 결제를 취소하려 하면 예외가 발생한다")
    void 타인_결제_취소는_예외가_발생한다() {
        Member currentMember = createMember(1L, "보호자");
        Member otherMember = createMember(2L, "다른보호자");
        PaymentOrder paymentOrder = createOrder(otherMember, "order_123", 10000, PaymentOrderStatus.PAID);
        Payment payment = createPayment(otherMember, paymentOrder, "pay_123", "order_123", 10000, PaymentStatus.DONE);

        given(memberUtil.getCurrentMember()).willReturn(currentMember);
        given(paymentRepository.findByPaymentKey("pay_123")).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.cancelPayment("pay_123", new CancelRequest("사용자 요청", null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        verify(paymentClient, never()).cancelPayment(any(), any());
    }

    @Test
    @DisplayName("이미 취소된 결제는 PG 재호출 없이 기존 상태를 반환한다")
    void 이미_취소된_결제는_멱등하게_처리한다() {
        Member member = createMember(1L, "보호자");
        PaymentOrder paymentOrder = createOrder(member, "order_123", 10000, PaymentOrderStatus.CANCELED);
        Payment payment = createPayment(member, paymentOrder, "pay_123", "order_123", 10000, PaymentStatus.DONE);
        payment.cancel(10000, 10000, "기존 취소", ZonedDateTime.now());

        given(memberUtil.getCurrentMember()).willReturn(member);
        given(paymentRepository.findByPaymentKey("pay_123")).willReturn(Optional.of(payment));

        PaymentConfirmResponse result = paymentService.cancelPayment("pay_123", new CancelRequest("다시 취소", null));

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(payment.getCancelReason()).isEqualTo("기존 취소");
        verify(paymentClient, never()).cancelPayment(any(), any());
    }

    @Test
    @DisplayName("부분 취소 시 취소 이력이 누적되고 상태가 PARTIAL_CANCELED로 변경된다")
    void 부분_취소() {
        Member member = createMember(1L, "보호자");
        given(member.getSeniorProfile().hasEnoughPoints(3000L)).willReturn(true);
        PaymentOrder paymentOrder = createOrder(member, "order_123", 10000, PaymentOrderStatus.PAID);
        Payment payment = createPayment(member, paymentOrder, "pay_123", "order_123", 10000, PaymentStatus.DONE);
        PaymentConfirmResponse partialCanceledResponse = createResponse("pay_123", "order_123", 10000, PaymentStatus.PARTIAL_CANCELED);

        given(memberUtil.getCurrentMember()).willReturn(member);
        given(paymentRepository.findByPaymentKey("pay_123")).willReturn(Optional.of(payment));
        given(paymentClient.cancelPayment("pay_123", new CancelRequest("부분 취소", 3000))).willReturn(partialCanceledResponse);

        PaymentConfirmResponse result = paymentService.cancelPayment("pay_123", new CancelRequest("부분 취소", 3000));

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
        assertThat(payment.getCanceledAmount()).isEqualTo(3000);
        assertThat(payment.getCanceledPointAmount()).isEqualTo(3000);
        assertThat(payment.getRemainingAmount()).isEqualTo(7000);
        assertThat(result.getCancellations()).hasSize(1);
        assertThat(paymentOrder.getStatus()).isEqualTo(PaymentOrderStatus.PAID);
        verify(seniorProfileService).deductPointsFromMember(member.getId(), 3000L, "부분 취소");
    }

    @Test
    @DisplayName("결제 취소에 필요한 포인트가 부족하면 PG 취소 전에 예외가 발생한다")
    void 결제_취소_전_포인트_부족_검증() {
        Member member = createMember(1L, "시니어");
        SeniorProfile seniorProfile = member.getSeniorProfile();
        given(seniorProfile.hasEnoughPoints(10000L)).willReturn(false);

        PaymentOrder paymentOrder = createOrder(member, "order_123", 10000, PaymentOrderStatus.PAID);
        Payment payment = createPayment(member, paymentOrder, "pay_123", "order_123", 10000, PaymentStatus.DONE);

        given(memberUtil.getCurrentMember()).willReturn(member);
        given(paymentRepository.findByPaymentKey("pay_123")).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.cancelPayment("pay_123", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BAD_REQUEST);

        verify(paymentClient, never()).cancelPayment(any(), any());
    }

    @Test
    @DisplayName("결제 승인 성공 시 패키지 포인트가 적립된다")
    void 결제_승인_성공_시_포인트_적립() {
        Member member = createMember(1L, "시니어");
        PaymentOrder paymentOrder = createOrder(member, "order_123", 10000, PaymentOrderStatus.CREATED);
        PaymentApproveRequest request = new PaymentApproveRequest("order_123", "pay_123");
        PaymentConfirmResponse approvedResponse = createResponse("pay_123", "order_123", 10000, PaymentStatus.DONE);

        given(memberUtil.getCurrentMember()).willReturn(member);
        given(paymentOrderRepository.findByOrderId("order_123")).willReturn(Optional.of(paymentOrder));
        given(paymentRepository.findByOrderId("order_123")).willReturn(Optional.empty());
        given(paymentRepository.findByPaymentKey("pay_123")).willReturn(Optional.empty());
        given(paymentClient.confirmPayment(any())).willReturn(approvedResponse);
        given(paymentRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        PaymentConfirmResponse result = paymentService.confirmPayment(request);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.DONE);
        verify(seniorProfileService).addPointsToMember(member.getId(), 10000L, "포인트 충전 10,000원");
    }

    @Test
    @DisplayName("PG 승인 성공 후 포인트 적립이 실패하면 예외가 전파되고 PG 호출은 이미 완료된다")
    void PG_승인_성공_후_포인트_적립_실패_시_예외가_전파된다() {
        Member member = createMember(1L, "시니어");
        PaymentOrder paymentOrder = createOrder(member, "order_123", 10000, PaymentOrderStatus.CREATED);
        PaymentApproveRequest request = new PaymentApproveRequest("order_123", "pay_123");
        PaymentConfirmResponse approvedResponse = createResponse("pay_123", "order_123", 10000, PaymentStatus.DONE);

        given(memberUtil.getCurrentMember()).willReturn(member);
        given(paymentOrderRepository.findByOrderId("order_123")).willReturn(Optional.of(paymentOrder));
        given(paymentRepository.findByOrderId("order_123")).willReturn(Optional.empty());
        given(paymentRepository.findByPaymentKey("pay_123")).willReturn(Optional.empty());
        given(paymentClient.confirmPayment(any())).willReturn(approvedResponse);
        given(paymentRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        org.mockito.BDDMockito.willThrow(new BusinessException(ErrorCode.POINT_CONCURRENT_UPDATE))
                .given(seniorProfileService)
                .addPointsToMember(member.getId(), 10000L, "포인트 충전 10,000원");

        assertThatThrownBy(() -> paymentService.confirmPayment(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POINT_CONCURRENT_UPDATE);

        verify(paymentClient).confirmPayment(any());
        verify(paymentRepository).save(any());
    }

    @Test
    @DisplayName("PG 승인 호출이 실패하면 내부 결제 저장과 포인트 적립을 시도하지 않는다")
    void PG_승인_호출_실패_시_내부_반영을_시도하지_않는다() {
        Member member = createMember(1L, "시니어");
        PaymentOrder paymentOrder = createOrder(member, "order_123", 10000, PaymentOrderStatus.CREATED);
        PaymentApproveRequest request = new PaymentApproveRequest("order_123", "pay_123");

        given(memberUtil.getCurrentMember()).willReturn(member);
        given(paymentOrderRepository.findByOrderId("order_123")).willReturn(Optional.of(paymentOrder));
        given(paymentRepository.findByOrderId("order_123")).willReturn(Optional.empty());
        given(paymentRepository.findByPaymentKey("pay_123")).willReturn(Optional.empty());
        given(paymentClient.confirmPayment(any())).willThrow(new IllegalStateException("PG timeout"));

        assertThatThrownBy(() -> paymentService.confirmPayment(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PG timeout");

        verify(paymentRepository, never()).save(any());
        verify(seniorProfileService, never()).addPointsToMember(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("PG 취소 성공 후 포인트 환수가 실패하면 예외가 전파되고 PG 취소는 이미 완료된다")
    void PG_취소_성공_후_포인트_환수_실패_시_예외가_전파된다() {
        Member member = createMember(1L, "시니어");
        given(member.getSeniorProfile().hasEnoughPoints(10000L)).willReturn(true);
        PaymentOrder paymentOrder = createOrder(member, "order_123", 10000, PaymentOrderStatus.PAID);
        Payment payment = createPayment(member, paymentOrder, "pay_123", "order_123", 10000, PaymentStatus.DONE);
        PaymentConfirmResponse canceledResponse = createResponse("pay_123", "order_123", 10000, PaymentStatus.CANCELED);

        given(memberUtil.getCurrentMember()).willReturn(member);
        given(paymentRepository.findByPaymentKey("pay_123")).willReturn(Optional.of(payment));
        given(paymentClient.cancelPayment("pay_123", new CancelRequest("사용자 요청", 10000))).willReturn(canceledResponse);
        org.mockito.BDDMockito.willThrow(new BusinessException(ErrorCode.POINT_CONCURRENT_UPDATE))
                .given(seniorProfileService)
                .deductPointsFromMember(member.getId(), 10000L, "사용자 요청");

        assertThatThrownBy(() -> paymentService.cancelPayment("pay_123", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POINT_CONCURRENT_UPDATE);

        verify(paymentClient).cancelPayment("pay_123", new CancelRequest("사용자 요청", 10000));
    }

    @Test
    @DisplayName("PG 취소 호출이 실패하면 포인트 환수를 시도하지 않는다")
    void PG_취소_호출_실패_시_포인트_환수를_시도하지_않는다() {
        Member member = createMember(1L, "시니어");
        given(member.getSeniorProfile().hasEnoughPoints(10000L)).willReturn(true);
        PaymentOrder paymentOrder = createOrder(member, "order_123", 10000, PaymentOrderStatus.PAID);
        Payment payment = createPayment(member, paymentOrder, "pay_123", "order_123", 10000, PaymentStatus.DONE);

        given(memberUtil.getCurrentMember()).willReturn(member);
        given(paymentRepository.findByPaymentKey("pay_123")).willReturn(Optional.of(payment));
        given(paymentClient.cancelPayment("pay_123", new CancelRequest("사용자 요청", 10000)))
                .willThrow(new IllegalStateException("PG timeout"));

        assertThatThrownBy(() -> paymentService.cancelPayment("pay_123", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PG timeout");

        verify(seniorProfileService, never()).deductPointsFromMember(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("시니어가 아닌 회원은 주문 생성이 불가능하다")
    void 시니어가_아니면_주문_생성_불가() {
        Member member = Member.createMember(MemberType.GUARDIAN, "보호자", "01012345678");
        ReflectionTestUtils.setField(member, "id", 1L);
        given(memberUtil.getCurrentMember()).willReturn(member);

        assertThatThrownBy(() -> paymentService.createOrder(new PaymentOrderCreateRequest("POINT_10000")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    private Member createMember(Long id, String name) {
        Member member = Member.createMember(MemberType.SENIOR, name, "01012345678");
        ReflectionTestUtils.setField(member, "id", id);
        SeniorProfile seniorProfile = org.mockito.Mockito.mock(SeniorProfile.class);
        ReflectionTestUtils.setField(member, "seniorProfile", seniorProfile);
        return member;
    }

    private PaymentOrder createOrder(Member member, String orderId, int amount, PaymentOrderStatus status) {
        PaymentOrder paymentOrder = PaymentOrder.create(
                orderId,
                member,
                "포인트 충전",
                "POINT_10000",
                amount,
                10000,
                ZonedDateTime.now().plusMinutes(15)
        );
        ReflectionTestUtils.setField(paymentOrder, "status", status);
        return paymentOrder;
    }

    private Payment createPayment(Member member, PaymentOrder paymentOrder, String paymentKey, String orderId,
                                  int amount, PaymentStatus status) {
        return Payment.builder()
                .member(member)
                .paymentOrder(paymentOrder)
                .paymentKey(paymentKey)
                .orderId(orderId)
                .orderName("포인트 충전")
                .amount(amount)
                .canceledAmount(0)
                .status(status)
                .requestedAt(ZonedDateTime.now().minusMinutes(1))
                .approvedAt(ZonedDateTime.now())
                .cultureExpense(false)
                .build();
    }

    private PaymentConfirmResponse createResponse(String paymentKey, String orderId, int amount, PaymentStatus status) {
        PaymentConfirmResponse response = new PaymentConfirmResponse();
        ReflectionTestUtils.setField(response, "paymentKey", paymentKey);
        ReflectionTestUtils.setField(response, "orderId", orderId);
        ReflectionTestUtils.setField(response, "amount", amount);
        ReflectionTestUtils.setField(response, "status", status);
        return response;
    }
}
