package com.widyu.sensor.repository;

import com.widyu.sensor.SensorBatchRejection;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SensorBatchRejectionRepository extends JpaRepository<SensorBatchRejection, Long> {

    /** 기간 내보내기(LLD-0108). 인덱스 {@code (member_id, received_at_ms)}를 탄다. */
    List<SensorBatchRejection> findByMemberIdAndReceivedAtMsBetweenOrderByReceivedAtMsAsc(
            Long memberId, Long startMs, Long endMs);

    /** 빠진 번호 대조(LLD-0166 5.3). 인덱스의 {@code member_id}만 탄다. 거절은 드물어 회원 단위로 훑는다. */
    List<SensorBatchRejection> findByMemberIdAndSessionIdIn(Long memberId, Collection<String> sessionIds);
}
