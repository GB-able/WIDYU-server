package com.widyu.sensor.repository;

import com.widyu.sensor.ActivityWindow;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ActivityWindowRepository extends JpaRepository<ActivityWindow, Long> {

    /** 같은 구간에 동시에 더하는 두 묶음이 서로의 합계를 덮지 않게 잠근다(LLD-0138 5.1). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from ActivityWindow w where w.memberId = :memberId and w.windowStartMs = :windowStartMs")
    Optional<ActivityWindow> findForUpdate(
            @Param("memberId") Long memberId, @Param("windowStartMs") Long windowStartMs);

    /** 심박 묶음 하나가 필요한 구간을 한 번에 읽는다. UK {@code (member_id, window_start_ms)}를 탄다. 양 끝 포함. */
    List<ActivityWindow> findByMemberIdAndWindowStartMsBetween(Long memberId, Long fromMs, Long toMs);
}
