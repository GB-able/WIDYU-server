package com.widyu.pay.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.widyu.auth.application.SmsService;
import com.widyu.auth.repository.RefreshTokenRepository;
import com.widyu.auth.repository.TemporaryMemberRepository;
import com.widyu.auth.repository.VerificationCodeRepository;
import com.widyu.global.util.MemberUtil;
import com.widyu.member.Family;
import com.widyu.member.Member;
import com.widyu.member.MemberType;
import com.widyu.member.PointHistoryType;
import com.widyu.member.SeniorProfile;
import com.widyu.member.repository.FamilyRepository;
import com.widyu.member.repository.MemberRepository;
import com.widyu.member.repository.PointHistoryRepository;
import com.widyu.member.repository.SeniorProfileRepository;
import com.widyu.pay.Payment;
import com.widyu.pay.PaymentOrder;
import com.widyu.pay.PaymentStatus;
import com.widyu.pay.application.PaymentService;
import com.widyu.pay.infrastructure.PaymentClient;
import com.widyu.pay.dto.request.CancelRequest;
import com.widyu.pay.dto.request.PaymentApproveRequest;
import com.widyu.pay.dto.request.PaymentOrderCreateRequest;
import com.widyu.pay.dto.response.PaymentConfirmResponse;
import com.widyu.pay.dto.response.PaymentOrderResponse;
import com.widyu.pay.repository.PaymentOrderRepository;
import com.widyu.pay.repository.PaymentRepository;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "firebase.config-path=/dev/null",
        "s3.credentials.access-key=test",
        "s3.credentials.secret-key=test",
        "s3.region.statics=ap-northeast-2",
        "s3.bucket-name=test-bucket",
        "coolsms.api-key=test",
        "coolsms.api-secret=test",
        "coolsms.api-url=https://api.coolsms.co.kr",
        "coolsms.from-phone-number=01000000000",
        "coolsms.verification-code-length=6",
        "coolsms.verification-code-ttl=300",
        "coolsms.message-template=인증번호: {code}"
})
@DisplayName("결제 통합 테스트")
class PaymentIntegrationTest {

    @Autowired private PaymentService paymentService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PaymentOrderRepository paymentOrderRepository;
    @Autowired private PointHistoryRepository pointHistoryRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private SeniorProfileRepository seniorProfileRepository;
    @Autowired private FamilyRepository familyRepository;

    @MockBean private PaymentClient paymentClient;
    @MockBean private MemberUtil memberUtil;
    @MockBean private VerificationCodeRepository verificationCodeRepository;
    @MockBean private TemporaryMemberRepository temporaryMemberRepository;
    @MockBean private RefreshTokenRepository refreshTokenRepository;
    @MockBean private S3Client s3Client;
    @MockBean private SmsService smsService;
    @MockBean private net.bramp.ffmpeg.FFmpeg ffmpeg;
    @MockBean private net.bramp.ffmpeg.FFprobe ffprobe;

    @AfterEach
    void tearDown() {
        pointHistoryRepository.deleteAll();
        paymentRepository.deleteAll();
        paymentOrderRepository.deleteAll();
        seniorProfileRepository.deleteAll();
        familyRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("결제 승인 시 주문, 결제, 포인트 적립 이력이 함께 저장된다")
    void 결제_승인_통합() {
        Member currentMember = createSeniorMember("홍길동", "01012345678", "FAM001", "INV0011");
        given(memberUtil.getCurrentMember()).willReturn(currentMember, currentMember);

        PaymentOrderResponse orderResponse = paymentService.createOrder(new PaymentOrderCreateRequest("POINT_10000"));
        given(paymentClient.confirmPayment(any())).willReturn(createGatewayResponse(
                "pay_123456",
                orderResponse.orderId(),
                "포인트 충전 10,000원",
                PaymentStatus.DONE
        ));
        PaymentConfirmResponse confirmResponse = paymentService.confirmPayment(
                new PaymentApproveRequest(orderResponse.orderId(), "pay_123456")
        );

        PaymentOrder paymentOrder = paymentOrderRepository.findByOrderId(orderResponse.orderId()).orElseThrow();
        Payment payment = paymentRepository.findByPaymentKey("pay_123456").orElseThrow();
        SeniorProfile seniorProfile = seniorProfileRepository.findByMemberId(currentMember.getId()).orElseThrow();
        var pointHistories = pointHistoryRepository.findAllBySeniorProfileIdOrderByCreatedAtDesc(seniorProfile.getId());

        assertThat(confirmResponse.getStatus()).isEqualTo(PaymentStatus.DONE);
        assertThat(paymentOrder.getStatus()).isEqualTo(com.widyu.pay.PaymentOrderStatus.PAID);
        assertThat(payment.getOrderId()).isEqualTo(orderResponse.orderId());
        assertThat(payment.getAmount()).isEqualTo(10000);
        assertThat(seniorProfile.getPoints()).isEqualTo(10100L);
        assertThat(pointHistories).hasSize(1);
        assertThat(pointHistories.get(0).getType()).isEqualTo(PointHistoryType.EARN);
        assertThat(pointHistories.get(0).getAmount()).isEqualTo(10000L);
        assertThat(pointHistories.get(0).getDescription()).isEqualTo("포인트 충전 10,000원");
    }

    @Test
    @DisplayName("부분 취소 시 취소 이력과 포인트 차감 이력이 함께 반영된다")
    void 부분_취소_통합() {
        Member currentMember = createSeniorMember("김영희", "01099998888", "FAM002", "INV0022");
        given(memberUtil.getCurrentMember()).willReturn(currentMember, currentMember);

        PaymentOrderResponse orderResponse = paymentService.createOrder(new PaymentOrderCreateRequest("POINT_10000"));
        given(paymentClient.confirmPayment(any())).willReturn(createGatewayResponse(
                "pay_654321",
                orderResponse.orderId(),
                "포인트 충전 10,000원",
                PaymentStatus.DONE
        ));
        paymentService.confirmPayment(new PaymentApproveRequest(orderResponse.orderId(), "pay_654321"));
        given(memberUtil.getCurrentMember()).willReturn(reloadCurrentMember(currentMember.getId()));
        given(paymentClient.cancelPayment("pay_654321", new CancelRequest("부분 취소", 3000)))
                .willReturn(createGatewayResponse(
                        "pay_654321",
                        orderResponse.orderId(),
                        "포인트 충전 10,000원",
                        PaymentStatus.PARTIAL_CANCELED
                ));
        PaymentConfirmResponse cancelResponse = paymentService.cancelPayment(
                "pay_654321",
                new CancelRequest("부분 취소", 3000)
        );

        Payment payment = paymentRepository.findByPaymentKey("pay_654321").orElseThrow();
        SeniorProfile seniorProfile = seniorProfileRepository.findByMemberId(currentMember.getId()).orElseThrow();
        List<com.widyu.member.PointHistory> pointHistories =
                pointHistoryRepository.findAllBySeniorProfileIdOrderByCreatedAtDesc(seniorProfile.getId());

        assertThat(cancelResponse.getStatus()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
        assertThat(payment.getCanceledAmount()).isEqualTo(3000);
        assertThat(payment.getCanceledPointAmount()).isEqualTo(3000);
        assertThat(payment.getRemainingAmount()).isEqualTo(7000);
        assertThat(cancelResponse.getCancellations()).hasSize(1);
        assertThat(seniorProfile.getPoints()).isEqualTo(7100L);
        assertThat(pointHistories).hasSize(2);
        assertThat(pointHistories.get(0).getType()).isEqualTo(PointHistoryType.USE);
        assertThat(pointHistories.get(0).getAmount()).isEqualTo(3000L);
        assertThat(pointHistories.get(0).getDescription()).isEqualTo("부분 취소");
    }

    private Member createSeniorMember(String name, String phoneNumber, String familyCode, String inviteCode) {
        Family family = familyRepository.save(Family.createFamily(familyCode));
        Member member = memberRepository.save(Member.createMember(MemberType.SENIOR, name, phoneNumber));
        SeniorProfile seniorProfile = seniorProfileRepository.save(
                SeniorProfile.createSeniorProfile(
                        member,
                        family,
                        "서울시 강남구",
                        inviteCode,
                        LocalDate.of(1950, 1, 1)
                )
        );
        ReflectionTestUtils.setField(member, "seniorProfile", seniorProfile);
        return member;
    }

    private Member reloadCurrentMember(Long memberId) {
        Member member = memberRepository.findById(memberId).orElseThrow();
        SeniorProfile seniorProfile = seniorProfileRepository.findByMemberId(memberId).orElseThrow();
        ReflectionTestUtils.setField(member, "seniorProfile", seniorProfile);
        return member;
    }

    private PaymentConfirmResponse createGatewayResponse(
            String paymentKey,
            String orderId,
            String orderName,
            PaymentStatus status
    ) {
        PaymentConfirmResponse response = new PaymentConfirmResponse();
        ReflectionTestUtils.setField(response, "paymentKey", paymentKey);
        ReflectionTestUtils.setField(response, "orderId", orderId);
        ReflectionTestUtils.setField(response, "orderName", orderName);
        ReflectionTestUtils.setField(response, "amount", 10000);
        ReflectionTestUtils.setField(response, "status", status);
        ReflectionTestUtils.setField(response, "requestedAt", ZonedDateTime.parse("2026-05-28T10:00:00+09:00"));
        ReflectionTestUtils.setField(response, "approvedAt", ZonedDateTime.parse("2026-05-28T10:01:00+09:00"));
        return response;
    }
}
