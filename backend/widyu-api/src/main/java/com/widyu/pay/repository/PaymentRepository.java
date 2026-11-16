package com.widyu.pay.repository;

import com.widyu.pay.Payment;
import com.widyu.pay.PaymentStatus;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentKey(String paymentKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.paymentKey = :paymentKey")
    Optional<Payment> findByPaymentKeyForUpdate(@Param("paymentKey") String paymentKey);

    // 결제 수단 4종(역방향 일대일, EAGER)과 취소 이력을 함께 읽어 결제마다 5건씩 더 나가지 않게 한다(LLD-0161).
    @Query("SELECT p FROM Payment p " +
           "LEFT JOIN FETCH p.card LEFT JOIN FETCH p.virtualAccount " +
           "LEFT JOIN FETCH p.transfer LEFT JOIN FETCH p.easyPay " +
           "LEFT JOIN FETCH p.cancellations " +
           "WHERE p.member.id = :memberId")
    List<Payment> findByMemberId(@Param("memberId") Long memberId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = :status AND p.approvedAt >= :since")
    long sumAmountSince(@Param("since") ZonedDateTime since, @Param("status") PaymentStatus status);

    @Query("SELECT p FROM Payment p JOIN FETCH p.member ORDER BY p.id DESC")
    Page<Payment> findAllForAdmin(Pageable pageable);

    long countByStatus(PaymentStatus status);

    List<Payment> findTop3ByMemberIdOrderByIdDesc(Long memberId);
    Optional<Payment> findByOrderId(String orderId);
}
