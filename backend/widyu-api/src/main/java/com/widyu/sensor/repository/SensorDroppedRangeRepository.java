package com.widyu.sensor.repository;

import com.widyu.sensor.SensorDroppedRange;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SensorDroppedRangeRepository extends JpaRepository<SensorDroppedRange, Long> {

    boolean existsByMemberIdAndSessionIdAndFromSeq(Long memberId, String sessionId, Long fromSeq);

    /** 기간 내보내기(LLD-0108). 인덱스 {@code (member_id, ts_ms)}를 탄다. */
    List<SensorDroppedRange> findByMemberIdAndTsMsBetweenOrderByTsMsAsc(Long memberId, Long startMs, Long endMs);

    /**
     * 빠진 번호 대조(LLD-0166 5.3). 큐 넘침 기록은 버린 묶음보다 최대 30분 늦어 시각으로 찾으면 빠지므로 세션으로 찾는다.
     * 유일 키 {@code (member_id, session_id, from_seq)}의 앞부분을 탄다.
     */
    List<SensorDroppedRange> findByMemberIdAndSessionIdIn(Long memberId, Collection<String> sessionIds);
}
