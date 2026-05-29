package com.widyu.pay.controller;

import com.widyu.global.response.ApiResponseTemplate;
import com.widyu.pay.application.PaymentService;
import com.widyu.pay.controller.docs.PaymentDocs;
import com.widyu.pay.dto.request.CancelRequest;
import com.widyu.pay.dto.request.PaymentApproveRequest;
import com.widyu.pay.dto.request.PaymentOrderCreateRequest;
import com.widyu.pay.dto.response.PaymentConfirmResponse;
import com.widyu.pay.dto.response.PaymentConfirmResponses;
import com.widyu.pay.dto.response.PaymentPackageResponse;
import com.widyu.pay.dto.response.PaymentOrderResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payment")
public class PaymentController implements PaymentDocs {

    private final PaymentService paymentService;

    @GetMapping("/packages")
    public ApiResponseTemplate<java.util.List<PaymentPackageResponse>> getPackages() {
        return ApiResponseTemplate.ok()
                .code("PAY_1999")
                .message("결제 패키지 조회 성공")
                .body(paymentService.getPackages());
    }

    @PostMapping("/orders")
    public ApiResponseTemplate<PaymentOrderResponse> createOrder(
            @RequestBody @Valid PaymentOrderCreateRequest paymentOrderCreateRequest
    ) {
        PaymentOrderResponse response = paymentService.createOrder(paymentOrderCreateRequest);

        return ApiResponseTemplate.ok()
                .code("PAY_2000")
                .message("주문 생성 성공")
                .body(response);
    }

    @PostMapping
    public ApiResponseTemplate<PaymentConfirmResponse> confirm(
            @RequestBody @Valid PaymentApproveRequest paymentApproveRequest
    ) {
        PaymentConfirmResponse response = paymentService.confirmPayment(paymentApproveRequest);

        return ApiResponseTemplate.ok()
                .code("PAY_2001")
                .message("결제 승인 성공")
                .body(response);
    }

    @PostMapping("/{paymentKey}/cancel")
    public ApiResponseTemplate<PaymentConfirmResponse> cancelPayment(
            @PathVariable String paymentKey,
            @RequestBody(required = false) @Valid CancelRequest cancelRequest
    ) {
        PaymentConfirmResponse response = paymentService.cancelPayment(paymentKey, cancelRequest);

        return ApiResponseTemplate.ok()
                .code("PAY_2002")
                .message("결제 취소 성공")
                .body(response);
    }

    @GetMapping("/me")
    public ApiResponseTemplate<PaymentConfirmResponses> getPaymentsByUser() {
        return ApiResponseTemplate.ok()
                .code("PAY_2003")
                .message("결제 목록 조회 성공")
                .body(paymentService.getPaymentsByUser());
    }
}
