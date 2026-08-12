package com.widyu.pay.infrastructure;

import com.widyu.pay.config.PaymentFeignConfig;
import com.widyu.pay.dto.request.PaymentGatewayCancelRequest;
import com.widyu.pay.dto.request.PaymentGatewayConfirmRequest;
import com.widyu.pay.dto.response.PaymentConfirmResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "paymentClient", url = "${spring.payment.base-url}", configuration = PaymentFeignConfig.class)
public interface PaymentClient {

    @GetMapping(value = "/{paymentKey}")
    PaymentConfirmResponse getPayment(@PathVariable("paymentKey") String paymentKey);

    @PostMapping(value = "/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    PaymentConfirmResponse confirmPayment(@RequestBody PaymentGatewayConfirmRequest paymentConfirmRequest,
                                          @RequestHeader("Idempotency-Key") String idempotencyKey);

    @PostMapping(value = "/{paymentKey}/cancel", consumes = MediaType.APPLICATION_JSON_VALUE)
    PaymentConfirmResponse cancelPayment(@PathVariable("paymentKey") String paymentKey,
                                         @RequestBody PaymentGatewayCancelRequest cancelRequest,
                                         @RequestHeader("Idempotency-Key") String idempotencyKey);
}
