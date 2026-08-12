package com.widyu.pay.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.SeniorProfile;
import com.widyu.global.util.MemberUtil;
import com.widyu.pay.PaymentStatus;
import com.widyu.pay.dto.request.CancelRequest;
import com.widyu.pay.dto.request.PaymentApproveRequest;
import com.widyu.pay.dto.request.PaymentGatewayCancelRequest;
import com.widyu.pay.dto.response.PaymentConfirmResponse;
import com.widyu.pay.infrastructure.PaymentClient;
import com.widyu.pay.infrastructure.PaymentGatewayException;
import com.widyu.pay.repository.PaymentOrderRepository;
import com.widyu.pay.repository.PaymentRepository;
import java.time.ZonedDateTime;
import java.util.List;
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
    @Mock private PaymentTransactionService paymentTransactionService;

    @InjectMocks private PaymentService paymentService;

    @Test
    @DisplayName("결제 승인하면 저장된 PG 멱등 키로 호출한다")
    void 결제_승인_시_저장된_PG_멱등_키를_전달한다() {
        // given
        Member member = createSeniorMember();
        PaymentApprovalCommand command = new PaymentApprovalCommand(
                "order_123", "pay_123", 10000, "approval-key", ZonedDateTime.now()
        );
        PaymentConfirmResponse response = createResponse("pay_123", "order_123", PaymentStatus.DONE);
        given(memberUtil.getCurrentMember()).willReturn(member);
        given(paymentTransactionService.claimApproval(any(), any()))
                .willReturn(new PaymentTransactionService.ApprovalClaim(null, command));
        given(paymentClient.confirmPayment(any(), anyString())).willReturn(response);
        given(paymentTransactionService.completeApproval(command, response)).willReturn(response);

        // when
        PaymentConfirmResponse result = paymentService.confirmPayment(new PaymentApproveRequest("order_123", "pay_123"));

        // then
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.DONE);
        verify(paymentClient).confirmPayment(any(), org.mockito.ArgumentMatchers.eq("approval-key"));
    }

    @Test
    @DisplayName("PG 승인 타임아웃이면 선점 상태를 유지한다")
    void PG_승인_타임아웃_시_선점_상태를_유지한다() {
        // given
        Member member = createSeniorMember();
        PaymentApprovalCommand command = new PaymentApprovalCommand(
                "order_123", "pay_123", 10000, "approval-key", ZonedDateTime.now()
        );
        given(memberUtil.getCurrentMember()).willReturn(member);
        given(paymentTransactionService.claimApproval(any(), any()))
                .willReturn(new PaymentTransactionService.ApprovalClaim(null, command));
        given(paymentClient.confirmPayment(any(), anyString())).willThrow(new IllegalStateException("timeout"));

        // when / then
        assertThatThrownBy(() -> paymentService.confirmPayment(new PaymentApproveRequest("order_123", "pay_123")))
                .isInstanceOf(IllegalStateException.class);
        verify(paymentTransactionService, never()).releaseApproval(any());
    }

    @Test
    @DisplayName("PG 승인 오류면 오류 코드를 보관하고 복구 대상으로 남긴다")
    void PG_승인_오류_시_복구_대상으로_남긴다() {
        // given
        Member member = createSeniorMember();
        PaymentApprovalCommand command = new PaymentApprovalCommand(
                "order_123", "pay_123", 10000, "approval-key", ZonedDateTime.now()
        );
        given(memberUtil.getCurrentMember()).willReturn(member);
        given(paymentTransactionService.claimApproval(any(), any()))
                .willReturn(new PaymentTransactionService.ApprovalClaim(null, command));
        given(paymentClient.confirmPayment(any(), anyString())).willThrow(
                new PaymentGatewayException(400, "PROVIDER_ERROR", "provider error")
        );

        // when / then
        assertThatThrownBy(() -> paymentService.confirmPayment(new PaymentApproveRequest("order_123", "pay_123")))
                .isInstanceOf(PaymentGatewayException.class);
        verify(paymentTransactionService).recordApprovalFailure(command, "PROVIDER_ERROR");
        verify(paymentTransactionService, never()).releaseApproval(command);
    }

    @Test
    @DisplayName("결제 취소하면 저장된 PG 멱등 키로 호출한다")
    void 결제_취소_시_저장된_PG_멱등_키를_전달한다() {
        // given
        Member member = createSeniorMember();
        CancelRequest request = new CancelRequest("사용자 요청", 10000);
        PaymentCancellationCommand command = new PaymentCancellationCommand(
                1L, "pay_123", request, "cancel-key", 10000, ZonedDateTime.now()
        );
        PaymentConfirmResponse response = createResponse("pay_123", "order_123", PaymentStatus.CANCELED);
        given(memberUtil.getCurrentMember()).willReturn(member);
        given(paymentTransactionService.claimCancellation("pay_123", request, member))
                .willReturn(new PaymentTransactionService.CancellationClaim(null, command));
        given(paymentClient.cancelPayment("pay_123", PaymentGatewayCancelRequest.from(request), "cancel-key")).willReturn(response);
        given(paymentTransactionService.completeCancellation(command, response)).willReturn(response);

        // when
        PaymentConfirmResponse result = paymentService.cancelPayment("pay_123", request);

        // then
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.CANCELED);
        verify(paymentClient).cancelPayment("pay_123", PaymentGatewayCancelRequest.from(request), "cancel-key");
    }

    @Test
    @DisplayName("복구 작업은 PG 승인 조회 결과를 내부에 반영한다")
    void 복구_작업은_PG_승인_조회_결과를_내부에_반영한다() {
        // given
        PaymentApprovalCommand command = new PaymentApprovalCommand(
                "order_123", "pay_123", 10000, "approval-key", ZonedDateTime.now()
        );
        PaymentConfirmResponse response = createResponse("pay_123", "order_123", PaymentStatus.DONE);
        given(paymentTransactionService.findPendingApprovals(any())).willReturn(List.of(command));
        given(paymentTransactionService.findPendingCancellations(any())).willReturn(List.of());
        given(paymentClient.getPayment("pay_123")).willReturn(response);

        // when
        paymentService.recoverPendingPayments();

        // then
        verify(paymentTransactionService).completeApproval(command, response);
        verify(paymentClient, never()).confirmPayment(any(), anyString());
    }

    @Test
    @DisplayName("복구 조회가 만료된 승인을 반환하면 선점을 해제한다")
    void 복구_조회가_만료된_승인을_반환하면_선점을_해제한다() {
        // given
        PaymentApprovalCommand command = new PaymentApprovalCommand(
                "order_123", "pay_123", 10000, "approval-key", ZonedDateTime.now()
        );
        PaymentConfirmResponse response = createResponse("pay_123", "order_123", PaymentStatus.EXPIRED);
        given(paymentTransactionService.findPendingApprovals(any())).willReturn(List.of(command));
        given(paymentTransactionService.findPendingCancellations(any())).willReturn(List.of());
        given(paymentClient.getPayment("pay_123")).willReturn(response);

        // when
        paymentService.recoverPendingPayments();

        // then
        verify(paymentTransactionService).releaseApproval(command);
        verify(paymentClient, never()).confirmPayment(any(), anyString());
    }

    @Test
    @DisplayName("PG 멱등 키 보존 기간이 지난 처리중 건은 조회 후 대사 대상으로 보존한다")
    void 만료된_처리중_건은_자동_POST를_중단한다() {
        // given
        PaymentApprovalCommand approvalCommand = new PaymentApprovalCommand(
                "order_123", "pay_123", 10000, "approval-key", ZonedDateTime.now().minusDays(16)
        );
        PaymentCancellationCommand cancellationCommand = new PaymentCancellationCommand(
                1L, "pay_456", new CancelRequest("사용자 요청", 10000), "cancel-key", 10000,
                ZonedDateTime.now().minusDays(16)
        );
        given(paymentTransactionService.findPendingApprovals(any())).willReturn(List.of(approvalCommand));
        given(paymentTransactionService.findPendingCancellations(any())).willReturn(List.of(cancellationCommand));
        given(paymentClient.getPayment("pay_123")).willReturn(createResponse("pay_123", "order_123", PaymentStatus.READY));
        given(paymentClient.getPayment("pay_456")).willReturn(createResponse("pay_456", "order_456", PaymentStatus.READY));

        // when
        paymentService.recoverPendingPayments();

        // then
        verify(paymentTransactionService).stopApprovalRecovery(approvalCommand, "AUTHORIZATION_EXPIRED");
        verify(paymentTransactionService).stopCancellationRecovery(cancellationCommand, "IDEMPOTENCY_KEY_EXPIRED");
        verify(paymentClient, never()).confirmPayment(any(), anyString());
        verify(paymentClient, never()).cancelPayment(anyString(), any(), anyString());
    }

    private Member createSeniorMember() {
        Member member = Member.createMember(MemberType.SENIOR, "시니어", "01012345678");
        ReflectionTestUtils.setField(member, "id", 1L);
        ReflectionTestUtils.setField(member, "seniorProfile", org.mockito.Mockito.mock(SeniorProfile.class));
        return member;
    }

    private PaymentConfirmResponse createResponse(String paymentKey, String orderId, PaymentStatus status) {
        PaymentConfirmResponse response = new PaymentConfirmResponse();
        ReflectionTestUtils.setField(response, "paymentKey", paymentKey);
        ReflectionTestUtils.setField(response, "orderId", orderId);
        ReflectionTestUtils.setField(response, "amount", 10000);
        ReflectionTestUtils.setField(response, "status", status);
        return response;
    }
}
