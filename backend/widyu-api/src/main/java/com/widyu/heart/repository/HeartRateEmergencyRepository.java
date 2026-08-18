package com.widyu.heart.repository;

import com.widyu.heart.HeartRateEmergency;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HeartRateEmergencyRepository extends JpaRepository<HeartRateEmergency, Long> {

    List<HeartRateEmergency> findByMemberIdOrderByMeasuredAtDesc(Long memberId);

    Optional<HeartRateEmergency> findFirstByMemberIdAndMeasuredAtAfterOrderByMeasuredAtAsc(Long memberId, LocalDateTime since);

    Optional<HeartRateEmergency> findFirstByMemberIdAndMeasuredAtAfterOrderByMeasuredAtDesc(Long memberId, LocalDateTime since);

    long countByMemberId(Long memberId);
    long countByMeasuredAtAfter(LocalDateTime measuredAt);
}
