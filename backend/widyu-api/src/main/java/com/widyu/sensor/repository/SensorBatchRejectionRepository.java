package com.widyu.sensor.repository;

import com.widyu.sensor.SensorBatchRejection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SensorBatchRejectionRepository extends JpaRepository<SensorBatchRejection, Long> {

    /** 기간 내보내기(LLD-0108). 인덱스 {@code (member_id, received_at_ms)}를 탄다. */
    List<SensorBatchRejection> findByMemberIdAndReceivedAtMsBetweenOrderByReceivedAtMsAsc(
            Long memberId, Long startMs, Long endMs);
}
