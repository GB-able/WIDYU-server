package com.widyu.sensor.application;

import com.widyu.sensor.SensorBatch;
import com.widyu.sensor.SensorBatchRejection;
import com.widyu.sensor.SensorDroppedRange;
import com.widyu.sensor.application.SensorWindowCsv.Window;
import com.widyu.sensor.repository.SensorBatchRejectionRepository;
import com.widyu.sensor.repository.SensorDroppedRangeRepository;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 빠진 묶음의 원인을 가리는 CSV 네 개를 만든다(LLD-0166 3.3, 5.4). 기간 내보내기와 회차 내보내기가 같이 쓴다.
 *
 * <p>버림·거절 목록 파일은 시각으로, 빠진 번호 대조는 세션으로 기록을 읽는다. 트랜잭션을 두지 않는다.
 * 저장소 조회가 각자 읽기 트랜잭션에서 끝난다. 원문과 센서 값은 다루지 않는다.
 */
@Service
@RequiredArgsConstructor
public class SensorLossDiagnostics {

    public static final String DROPPED_FILE = "dropped_ranges.csv";
    public static final String REJECTIONS_FILE = "rejections.csv";
    public static final String GAPS_FILE = "seq_gaps.csv";
    public static final String SESSIONS_FILE = "seq_sessions.csv";

    private final SensorDroppedRangeRepository sensorDroppedRangeRepository;
    private final SensorBatchRejectionRepository sensorBatchRejectionRepository;

    /** 파일 하나. 머리글만 있으면 "찾아봤는데 없다"는 뜻이다. */
    public record CsvFile(String name, String header, List<String> rows) {}

    /**
     * @param batches 내보내는 묶음(재전송 포함). 이 묶음의 세션만 대조한다
     * @param fromMs  phase 기준 시작(요청 시각 또는 회차 시작)
     * @param toMs    phase 기준 끝
     * @param startMs 목록 파일을 고르는 구간 시작(여유 포함)
     * @param endMs   목록 파일을 고르는 구간 끝(여유 포함)
     */
    public List<CsvFile> files(Long memberId, List<SensorBatch> batches,
                               long fromMs, long toMs, long startMs, long endMs) {
        Window window = new Window(fromMs, toMs, startMs, endMs);
        List<SensorDroppedRange> listedDropped = sensorDroppedRangeRepository
                .findByMemberIdAndTsMsBetweenOrderByTsMsAsc(memberId, startMs, endMs);
        List<SensorBatchRejection> listedRejections = sensorBatchRejectionRepository
                .findByMemberIdAndReceivedAtMsBetweenOrderByReceivedAtMsAsc(memberId, startMs, endMs);

        Set<String> sessionIds = sessionIds(batches);
        List<SensorDroppedRange> sessionDropped = List.of();
        List<SensorBatchRejection> sessionRejections = List.of();
        if (!sessionIds.isEmpty()) {
            sessionDropped = sensorDroppedRangeRepository.findByMemberIdAndSessionIdIn(memberId, sessionIds);
            sessionRejections = sensorBatchRejectionRepository.findByMemberIdAndSessionIdIn(memberId, sessionIds);
        }
        SensorSeqGapReport.Lines report = SensorSeqGapReport.build(batches, sessionDropped, sessionRejections);

        return List.of(
                new CsvFile(DROPPED_FILE, SensorWindowCsv.DROPPED_HEADER,
                        listedDropped.stream().map(range -> SensorWindowCsv.dropped(range, window)).toList()),
                new CsvFile(REJECTIONS_FILE, SensorWindowCsv.REJECTION_HEADER,
                        listedRejections.stream().map(rejection -> SensorWindowCsv.rejection(rejection, window))
                                .toList()),
                new CsvFile(GAPS_FILE, SensorSeqGapReport.GAP_HEADER, report.gaps()),
                new CsvFile(SESSIONS_FILE, SensorSeqGapReport.SESSION_HEADER, report.sessions()));
    }

    /** 재전송이 가리키는 원 세션도 넣는다. 원 번호가 그 세션의 빈칸을 채운다. */
    private static Set<String> sessionIds(List<SensorBatch> batches) {
        Set<String> sessionIds = new TreeSet<>();
        for (SensorBatch batch : batches) {
            sessionIds.add(batch.getSessionId());
            if (Boolean.TRUE.equals(batch.getIsResend()) && batch.getOriginalSessionId() != null) {
                sessionIds.add(batch.getOriginalSessionId());
            }
        }
        return sessionIds;
    }
}
