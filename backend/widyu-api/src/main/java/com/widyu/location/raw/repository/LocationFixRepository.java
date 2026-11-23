package com.widyu.location.raw.repository;

import com.widyu.location.raw.LocationFix;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LocationFixRepository extends JpaRepository<LocationFix, Long> {

    boolean existsByDeviceIdAndSessionIdAndSeq(String deviceId, String sessionId, Long seq);

    /** 내보내기: 회차의 위치 원문을 잰 시각 순으로(LLD-0050 5.3). */
    java.util.List<LocationFix> findByRunIdOrderByTsMsAscSeqAsc(String runId);

    /** 시간대 내보내기: 한 회원의 구간 안 위치 원문을 잰 시각 순으로(LLD-0108 4절). 양 끝 포함. */
    java.util.List<LocationFix> findByMemberIdAndTsMsBetweenOrderByTsMsAscSeqAsc(
            Long memberId, Long startMs, Long endMs);

    /** 어르신 탈퇴 때 위치 원본을 바로 지운다(LLD-0186). 인덱스 {@code (member_id, ts_ms)}. */
    // ponytail: 한 문장 DELETE. 위치 원본 신규 저장이 멈춰(ADR-0045) 행 수가 더 늘지 않는다. 커지면 PK 묶음 삭제로 나눈다
    // 컨텍스트를 비우지 않는다. 같은 트랜잭션의 아직 flush하지 않은 탈퇴 처리 변경이 버려진다.
    @Modifying
    @Query("DELETE FROM LocationFix l WHERE l.memberId = :memberId")
    int deleteByMemberId(@Param("memberId") Long memberId);
}
