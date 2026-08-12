package com.widyu.pay.repository;

import com.widyu.pay.PaymentOrder;
import java.util.Optional;
import java.util.List;
import java.time.ZonedDateTime;
import com.widyu.pay.PaymentOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    Optional<PaymentOrder> findByOrderId(String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentOrder p where p.orderId = :orderId")
    Optional<PaymentOrder> findByOrderIdForUpdate(@Param("orderId") String orderId);

    boolean existsByOrderId(String orderId);

    List<PaymentOrder> findByStatusAndApprovalNextRetryAtBeforeAndApprovalRecoveryStoppedAtIsNull(
            PaymentOrderStatus status,
            ZonedDateTime nextRetryAt
    );
}
