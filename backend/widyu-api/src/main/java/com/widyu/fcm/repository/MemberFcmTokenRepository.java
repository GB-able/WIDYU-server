package com.widyu.fcm.repository;

import com.widyu.fcm.MemberFcmToken;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MemberFcmTokenRepository extends JpaRepository<MemberFcmToken, Long> {
    Optional<MemberFcmToken> findByToken(String fcmToken);

    List<MemberFcmToken> findAllByMemberIdAndActiveTrue(Long id);

    List<MemberFcmToken> findAllByLastUsedAtBeforeAndActiveTrue(LocalDateTime threshold);

    long countByMemberIdAndActiveTrue(Long memberId);

    long countByActiveTrue();

    long countByActiveFalse();

    @Query("SELECT COUNT(DISTINCT t.member.id) FROM MemberFcmToken t WHERE t.active = true")
    long countDistinctMembersWithActiveToken();

    @Query("SELECT t FROM MemberFcmToken t JOIN FETCH t.member WHERE t.active = false ORDER BY t.expiredAt DESC")
    List<MemberFcmToken> findTop10InactiveOrderByExpiredAtDesc(Pageable pageable);
}

