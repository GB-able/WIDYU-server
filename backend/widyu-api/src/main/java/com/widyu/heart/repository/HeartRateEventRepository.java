package com.widyu.heart.repository;

import com.widyu.heart.HeartRateEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface HeartRateEventRepository extends JpaRepository<HeartRateEvent, Long> {

    boolean existsByMemberIdAndMeasuredAt(Long memberId, LocalDateTime measuredAt);

    List<HeartRateEvent> findByMemberIdOrderByMeasuredAtAsc(Long memberId);

    List<HeartRateEvent> findTop5ByMemberIdOrderByMeasuredAtDesc(Long memberId);

    Optional<HeartRateEvent> findFirstByMemberIdOrderByMeasuredAtDesc(Long memberId);

    List<HeartRateEvent> findTop15ByMemberIdOrderByMeasuredAtDesc(Long memberId);

    @Query("SELECT MAX(h.heartRate) FROM HeartRateEvent h WHERE h.member.id = :memberId")
    Optional<Integer> findMaxHeartRateByMemberId(@Param("memberId") Long memberId);

    @Query("SELECT MIN(h.heartRate) FROM HeartRateEvent h WHERE h.member.id = :memberId")
    Optional<Integer> findMinHeartRateByMemberId(@Param("memberId") Long memberId);

    @Query("SELECT TIMESTAMPDIFF(MINUTE, MIN(h.measuredAt), MAX(h.measuredAt)) FROM HeartRateEvent h WHERE h.member.id = :memberId")
    Optional<Integer> findTotalDurationMinutesByMemberId(@Param("memberId") Long memberId);

    @Modifying
    @Query("DELETE FROM HeartRateEvent h WHERE h.measuredAt < :before")
    int deleteByMeasuredAtBefore(@Param("before") LocalDateTime before);
}
