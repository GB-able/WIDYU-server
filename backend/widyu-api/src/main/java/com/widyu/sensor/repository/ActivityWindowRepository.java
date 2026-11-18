package com.widyu.sensor.repository;

import com.widyu.sensor.ActivityWindow;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ActivityWindowRepository extends JpaRepository<ActivityWindow, Long> {

    /**
     * 구간 행이 없을 때만 빈 행(합계 0)으로 넣고, 있으면 아무것도 바꾸지 않는다(MySQL 전용, LLD-0138 5.1).
     * 넣은 행이든 이미 있던 행이든 커밋까지 배타 잠금을 쥐며, 다른 트랜잭션이 넣고 커밋하지 않은 행이면 그 커밋을 기다린다.
     * 중복 키를 예외로 받지 않아 동시 첫 저장이 교착이나 Hibernate ERROR 로그를 남기지 않는다(#1093).
     */
    @Modifying
    @Query(value = """
            INSERT INTO activity_window (member_id, window_start_ms, window_end_ms, sample_count,
                                         magnitude_sum_mg, magnitude_sq_sum_mg, created_at, updated_at)
            VALUES (:memberId, :windowStartMs, :windowEndMs, 0, 0, 0, :now, :now)
            ON DUPLICATE KEY UPDATE member_id = member_id
            """, nativeQuery = true)
    void insertIfAbsent(@Param("memberId") Long memberId, @Param("windowStartMs") long windowStartMs,
                        @Param("windowEndMs") long windowEndMs, @Param("now") LocalDateTime now);

    /** 같은 구간에 동시에 더하는 두 묶음이 서로의 합계를 덮지 않게 잠근다(LLD-0138 5.1). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from ActivityWindow w where w.memberId = :memberId and w.windowStartMs = :windowStartMs")
    Optional<ActivityWindow> findForUpdate(
            @Param("memberId") Long memberId, @Param("windowStartMs") Long windowStartMs);

    /** 심박 묶음 하나가 필요한 구간을 한 번에 읽는다. UK {@code (member_id, window_start_ms)}를 탄다. 양 끝 포함. */
    List<ActivityWindow> findByMemberIdAndWindowStartMsBetween(Long memberId, Long fromMs, Long toMs);
}
