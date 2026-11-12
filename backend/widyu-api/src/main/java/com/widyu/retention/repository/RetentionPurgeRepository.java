package com.widyu.retention.repository;

import com.widyu.retention.application.RetentionTarget;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 보관 기간이 지난 행을 PK 범위로 찾는다(LLD-0136 5.2).
 *
 * <p>대상 테이블에는 기준 시각 인덱스가 없다. 기준 시각이 PK와 함께 늘어나므로 PK 이분 탐색으로
 * 경계를 찾고, 경계 앞의 PK 범위만 센다. 기간 안의 행은 읽지 않는다.
 */
@Repository
@RequiredArgsConstructor
public class RetentionPurgeRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * 그 PK부터 처음 만나는 행이 컷오프 이후인 가장 작은 PK 값. 이보다 작은 PK의 행은 기간이 지났다.
     * 표가 비었으면 0. 탐색 한 번은 PK 인덱스로 한 행만 읽는다.
     */
    public long findBoundary(RetentionTarget target, LocalDateTime cutoff) {
        Map<String, Object> range = jdbc.queryForMap(
                "SELECT MIN(" + target.pkColumn() + ") AS lo, MAX(" + target.pkColumn() + ") AS hi FROM "
                        + target.table(), Map.of());
        if (range.get("lo") == null) {
            return 0;
        }
        long lo = ((Number) range.get("lo")).longValue();
        long hi = ((Number) range.get("hi")).longValue() + 1;
        while (lo < hi) {
            long mid = lo + (hi - lo) / 2;
            if (isFreshFrom(target, mid, cutoff)) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return lo;
    }

    public long countTargets(RetentionTarget target, long bound, LocalDateTime cutoff, LocalDateTime now) {
        return count(target, target.targetCondition(), bound, cutoff, now);
    }

    public long countExempt(RetentionTarget target, long bound, LocalDateTime cutoff, LocalDateTime now) {
        return count(target, target.exemptCondition(), bound, cutoff, now);
    }

    private boolean isFreshFrom(RetentionTarget target, long pk, LocalDateTime cutoff) {
        List<LocalDateTime> times = jdbc.queryForList(
                "SELECT " + target.timeColumn() + " FROM " + target.table()
                        + " WHERE " + target.pkColumn() + " >= :pk ORDER BY " + target.pkColumn() + " LIMIT 1",
                Map.of("pk", pk), LocalDateTime.class);
        return times.isEmpty() || !times.get(0).isBefore(cutoff);
    }

    private long count(RetentionTarget target, String condition, long bound,
                       LocalDateTime cutoff, LocalDateTime now) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + target.table() + " WHERE " + condition,
                new MapSqlParameterSource()
                        .addValue("bound", bound)
                        .addValue("cutoff", cutoff)
                        .addValue("now", now),
                Long.class);
        return count;
    }
}
