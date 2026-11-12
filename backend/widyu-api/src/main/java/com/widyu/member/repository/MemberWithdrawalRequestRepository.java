package com.widyu.member.repository;

import com.widyu.member.MemberWithdrawalRequest;
import com.widyu.member.MemberWithdrawalRequestStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberWithdrawalRequestRepository extends JpaRepository<MemberWithdrawalRequest, Long> {

    Optional<MemberWithdrawalRequest> findFirstByMemberIdAndStatus(Long memberId, MemberWithdrawalRequestStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM MemberWithdrawalRequest r WHERE r.id = :requestId")
    Optional<MemberWithdrawalRequest> findByIdForUpdate(@Param("requestId") Long requestId);

    Page<MemberWithdrawalRequest> findAllByOrderByIdAsc(Pageable pageable);

    Page<MemberWithdrawalRequest> findByStatusOrderByIdAsc(MemberWithdrawalRequestStatus status, Pageable pageable);
}
