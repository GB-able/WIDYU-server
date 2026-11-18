package com.widyu.sensor.repository;

import com.widyu.sensor.ClockMapping;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ClockMappingRepository extends JpaRepository<ClockMapping, Long> {

    Optional<ClockMapping> findByClockMappingId(String clockMappingId);

    /**
     * 같은 {@code clock_mapping_id} 행이 없을 때만 넣고, 있으면 아무것도 바꾸지 않는다(MySQL 전용).
     * 동시 첫 등록의 UK 경합을 예외 없이 넘겨 Hibernate {@code SqlExceptionHelper}가 정상 경합을 ERROR로 남기지 않게 한다.
     * {@code ON DUPLICATE KEY}는 중복 키만 넘기므로 다른 제약 위반은 지금처럼 예외로 올라온다({@code INSERT IGNORE}와 다르다).
     */
    @Modifying
    @Query(value = """
            INSERT INTO clock_mapping (clock_mapping_id, device_id, boot_id, anchor_elapsed_ns, anchor_epoch_ms,
                uncertainty_ms, observed_min_elapsed_ns, observed_max_elapsed_ns, first_seen_at_ms, last_seen_at_ms,
                created_at, updated_at)
            VALUES (:#{#m.clockMappingId}, :#{#m.deviceId}, :#{#m.bootId}, :#{#m.anchorElapsedNs}, :#{#m.anchorEpochMs},
                :#{#m.uncertaintyMs}, :#{#m.observedMinElapsedNs}, :#{#m.observedMaxElapsedNs}, :#{#m.firstSeenAtMs},
                :#{#m.lastSeenAtMs}, :now, :now)
            ON DUPLICATE KEY UPDATE clock_mapping_id = clock_mapping_id
            """, nativeQuery = true)
    void insertIfAbsent(@Param("m") ClockMapping mapping, @Param("now") LocalDateTime now);

    /**
     * 관측 범위를 이 배치까지 덮도록 넓힌다. 읽고 비교해 저장하면 동시 갱신에서 한쪽이 덮이므로
     * 한 문장으로 처리한다(LLD-0044 5.1 4단계).
     */
    @Modifying
    @Query("""
            UPDATE ClockMapping m
               SET m.observedMinElapsedNs =
                       CASE WHEN m.observedMinElapsedNs < :min THEN m.observedMinElapsedNs ELSE :min END,
                   m.observedMaxElapsedNs =
                       CASE WHEN m.observedMaxElapsedNs > :max THEN m.observedMaxElapsedNs ELSE :max END,
                   m.lastSeenAtMs = :now
             WHERE m.clockMappingId = :clockMappingId
            """)
    int widenObservedRange(
            @Param("clockMappingId") String clockMappingId,
            @Param("min") long min,
            @Param("max") long max,
            @Param("now") long now);

    /** 내보내기: 회차 배치가 참조하는 매핑들(LLD-0050 5.4). */
    java.util.List<ClockMapping> findByClockMappingIdIn(java.util.Collection<String> clockMappingIds);
}
