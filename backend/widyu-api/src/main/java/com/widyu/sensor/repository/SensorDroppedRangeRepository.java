package com.widyu.sensor.repository;

import com.widyu.sensor.SensorDroppedRange;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SensorDroppedRangeRepository extends JpaRepository<SensorDroppedRange, Long> {

    boolean existsByMemberIdAndSessionIdAndFromSeq(Long memberId, String sessionId, Long fromSeq);

    /** 기간 내보내기(LLD-0108). 인덱스 {@code (member_id, ts_ms)}를 탄다. */
    List<SensorDroppedRange> findByMemberIdAndTsMsBetweenOrderByTsMsAsc(Long memberId, Long startMs, Long endMs);
}
