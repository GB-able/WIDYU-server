package com.widyu.fcm.repository;

import com.widyu.fcm.MemberFcmToken;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberFcmTokenRepository extends JpaRepository<MemberFcmToken, Long> {
    Optional<MemberFcmToken> findByToken(String fcmToken);

    List<MemberFcmToken> findAllByMemberIdAndActiveTrue(Long id);

    List<MemberFcmToken> findAllByLastUsedAtBeforeAndActiveTrue(LocalDateTime threshold);
}

