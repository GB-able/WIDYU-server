package com.widyu.pay.application;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentRecoveryScheduler {

    private final PaymentService paymentService;

    @Scheduled(fixedDelay = 60_000)
    public void recoverPendingPayments() {
        // ponytail: single-node scheduler, add ShedLock before running multiple API instances.
        paymentService.recoverPendingPayments();
    }
}
