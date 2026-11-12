package com.widyu.retention;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 보관 기간 정리 작업 한 번, 테이블 하나의 결과(LLD-0136 4.3).
 *
 * <p>회원 ID, 좌표, 건강값을 담지 않는다. 테이블 이름과 건수만 남긴다.
 * 실패하면 건수는 비우고 예외 클래스 이름만 남긴다.
 */
@Entity
@Getter
@Table(
        name = "data_purge_log",
        indexes = @Index(name = "idx_data_purge_log_started", columnList = "started_at")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DataPurgeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "data_purge_log_id")
    private Long id;

    /** 한 번의 실행을 묶는 UUID. */
    @Column(name = "run_id", nullable = false, length = 36)
    private String runId;

    /** 대상 테이블 이름. */
    @Column(name = "target", nullable = false, length = 40)
    private String target;

    /** 지우지 않고 세기만 했는지. */
    @Column(name = "dry_run", nullable = false)
    private boolean dryRun;

    /** 이 시각보다 앞선 행이 대상이다. */
    @Column(name = "cutoff_at", nullable = false)
    private LocalDateTime cutoffAt;

    /** 조건에 맞는 행 수(실증 참가자 제외 뒤). 실패하면 null. */
    @Column(name = "target_rows")
    private Long targetRows;

    /** 기간이 지났지만 실증 참가자라 뺀 행 수. 제외 규칙이 없는 테이블과 실패하면 null. */
    @Column(name = "exempt_rows")
    private Long exemptRows;

    @Column(name = "error_type", length = 100)
    private String errorType;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at", nullable = false)
    private LocalDateTime finishedAt;

    private DataPurgeLog(String runId, String target, boolean dryRun, LocalDateTime cutoffAt,
                         Long targetRows, Long exemptRows, String errorType,
                         LocalDateTime startedAt, LocalDateTime finishedAt) {
        this.runId = runId;
        this.target = target;
        this.dryRun = dryRun;
        this.cutoffAt = cutoffAt;
        this.targetRows = targetRows;
        this.exemptRows = exemptRows;
        this.errorType = errorType;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    public static DataPurgeLog counted(String runId, String target, LocalDateTime cutoffAt,
                                       long targetRows, Long exemptRows,
                                       LocalDateTime startedAt, LocalDateTime finishedAt) {
        return new DataPurgeLog(runId, target, true, cutoffAt, targetRows, exemptRows, null,
                startedAt, finishedAt);
    }

    public static DataPurgeLog failed(String runId, String target, LocalDateTime cutoffAt,
                                      String errorType, LocalDateTime startedAt, LocalDateTime finishedAt) {
        return new DataPurgeLog(runId, target, true, cutoffAt, null, null, errorType,
                startedAt, finishedAt);
    }
}
