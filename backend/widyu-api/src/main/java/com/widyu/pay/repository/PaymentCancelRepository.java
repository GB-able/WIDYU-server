package com.widyu.pay.repository;

import com.widyu.pay.PaymentCancel;
import com.widyu.pay.PaymentCancelStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentCancelRepository extends JpaRepository<PaymentCancel, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentCancel p where p.id = :id")
    Optional<PaymentCancel> findByIdForUpdate(@Param("id") Long id);

    List<PaymentCancel> findByStatusAndNextRetryAtBeforeAndRecoveryStoppedAtIsNull(
            PaymentCancelStatus status,
            ZonedDateTime nextRetryAt
    );
}
