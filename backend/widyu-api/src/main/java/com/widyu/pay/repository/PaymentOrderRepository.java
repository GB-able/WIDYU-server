package com.widyu.pay.repository;

import com.widyu.pay.PaymentOrder;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    Optional<PaymentOrder> findByOrderId(String orderId);

    boolean existsByOrderId(String orderId);
}
