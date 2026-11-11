package com.widyu.location.access.repository;

import com.widyu.location.access.LocationAccessLog;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 추가 전용 테이블이라 조회와 {@code notified_at} 갱신만 정의한다(LLD-0056 4절).
 */
@Repository
public interface LocationAccessLogRepository extends JpaRepository<LocationAccessLog, Long> {

    Page<LocationAccessLog> findBySeniorMemberIdAndAccessedAtBetween(
            Long seniorMemberId, LocalDateTime from, LocalDateTime to, Pageable pageable);

    /** 다이제스트 대상 시니어. 즉시 통보 FCM 등록이 실패해 롤백되지 않은 채 남은 옛 행 등(LLD-0130 5.3). */
    @Query("select distinct l.seniorMemberId from LocationAccessLog l where l.notifiedAt is null")
    List<Long> findSeniorIdsWithUnnotified();

    /**
     * 한 시니어의 미통보 행을 한 번에 선점한다. 1 이상을 돌려주면 이 인스턴스가 집은 것이고,
     * 0이면 다른 인스턴스가 먼저 집었으니 통보하지 않는다(LLD-0056 5.4).
     *
     * <p>읽고-보내고-갱신하면 인스턴스가 여럿일 때 같은 행을 저마다 읽어 중복 통보가 나간다.
     * 조건부 UPDATE 한 방이 그 경합을 없앤다 — {@code RunExportRepository.claim}과 같은 방식이다.
     *
     * <p>호출자인 {@code LocationAccessDigestSender}의 트랜잭션에 참여한다. FCM 등록이 실패하면
     * 이 갱신도 롤백되어 다음 실행에서 다시 선점할 수 있다.
     */
    @Modifying(clearAutomatically = true)
    @Query("update LocationAccessLog l set l.notifiedAt = :now"
            + " where l.seniorMemberId = :seniorMemberId and l.notifiedAt is null")
    int claimUnnotified(
            @Param("seniorMemberId") Long seniorMemberId, @Param("now") LocalDateTime now);

    /** 방금 선점한 행. 통보 문구의 조회자 수·횟수를 여기서 센다. */
    List<LocationAccessLog> findBySeniorMemberIdAndNotifiedAt(
            Long seniorMemberId, LocalDateTime notifiedAt);
}
