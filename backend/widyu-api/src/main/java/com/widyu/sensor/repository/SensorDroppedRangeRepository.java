package com.widyu.sensor.repository;

import com.widyu.sensor.SensorDroppedRange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SensorDroppedRangeRepository extends JpaRepository<SensorDroppedRange, Long> {

    boolean existsByMemberIdAndSessionIdAndFromSeq(Long memberId, String sessionId, Long fromSeq);
}
