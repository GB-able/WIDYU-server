package com.widyu.pay.repository;

import com.widyu.pay.Payment;
import com.widyu.pay.PaymentStatus;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentKey(String paymentKey);

    List<Payment> findByMemberId(Long memberId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = :status AND p.approvedAt >= :since")
    long sumAmountSince(@Param("since") ZonedDateTime since, @Param("status") PaymentStatus status);
}
