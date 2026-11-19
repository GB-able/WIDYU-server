package com.widyu.fcm.repository;

import com.widyu.fcm.FcmOutbox;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface FcmOutboxRepository extends JpaRepository<FcmOutbox, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from FcmOutbox o where o.id = :id")
    Optional<FcmOutbox> lockById(Long id);

    @Query("""
            select o.id from FcmOutbox o
            where (o.state = com.widyu.fcm.FcmOutbox.State.PENDING and (o.availableAt <= :now or o.expiresAt <= :now))
               or (o.state = com.widyu.fcm.FcmOutbox.State.CLAIMED and o.leaseUntil <= :now)
            order by o.id
            """)
    List<Long> findDue(LocalDateTime now, Pageable pageable);

    Optional<FcmOutbox> findByIdAndRecipientMember_Id(Long id, Long recipientMemberId);

    /** 폰 도착 신호는 첫 값만 남긴다. 재전송된 신호가 동시에 와도 조건부 UPDATE 하나라 한 번만 성공한다(LLD-0181). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update FcmOutbox o set o.deviceReceivedAt = :receivedAt, o.deviceReportedAtMs = :reportedAtMs "
            + "where o.id = :id and o.deviceReceivedAt is null")
    int markDeviceReceivedIfFirst(Long id, LocalDateTime receivedAt, Long reportedAtMs);
}
