package com.widyu.sensor.application;

import static com.widyu.sensor.application.SensorWindowCsv.kst;
import static com.widyu.sensor.application.SensorWindowCsv.line;

import com.widyu.sensor.SensorBatch;
import com.widyu.sensor.SensorBatchRejection;
import com.widyu.sensor.SensorDroppedRange;
import com.widyu.sensor.SensorIngestChannel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * 세션 안에서 빠진 묶음 번호 구간을 찾아 기기 버림 기록, 서버 거절 기록과 대조한다(LLD-0166 5.3). 상태 없는 계산이다.
 *
 * <p>재전송 묶음은 원 세션의 원 번호를 채운다. 세션의 처음과 끝 번호 밖은 보지 않는다. 내보내기 구간이 세션 중간에서
 * 시작하거나 끝날 수 있어, 그 밖을 빠짐으로 적으면 구간 밖 묶음이 모두 빠진 것으로 나온다.
 */
final class SensorSeqGapReport {

    static final String GAP_HEADER = line("stream", "device_id", "session_id", "from_seq", "to_seq",
            "missing_count", "gap_start_ms", "gap_start_kst", "gap_end_ms", "gap_end_kst", "dropped_count",
            "dropped_reasons", "rejected_count", "rejection_codes", "rejection_channels", "unexplained_count", "cause");
    static final String SESSION_HEADER = line("stream", "device_id", "session_id", "first_seq", "last_seq",
            "expected_count", "received_count", "missing_count", "missing_ratio", "gap_count", "ws_count",
            "rest_count");

    static final String CAUSE_DEVICE_DROPPED = "DEVICE_DROPPED";
    static final String CAUSE_SERVER_REJECTED = "SERVER_REJECTED";
    static final String CAUSE_NO_RECORD = "NO_RECORD";

    private SensorSeqGapReport() {
    }

    record Lines(List<String> gaps, List<String> sessions) {}

    private record Key(String stream, String sessionId) {}

    /** 한 세션의 재료. 시각은 그 번호 묶음(재전송으로 채운 번호는 재전송 묶음)의 측정 시작·끝이다. */
    private static final class Session {
        private String deviceId;
        private final TreeMap<Long, long[]> measured = new TreeMap<>();
        private final TreeSet<Long> received = new TreeSet<>();
        private int wsCount;
        private int restCount;
    }

    static Lines build(List<SensorBatch> batches, List<SensorDroppedRange> droppedRanges,
                       List<SensorBatchRejection> rejections) {
        Map<Key, Session> sessions = sessions(batches);
        List<String> gapRows = new ArrayList<>();
        List<String> sessionRows = new ArrayList<>();
        for (Entry<Key, Session> entry : sessions.entrySet()) {
            Key key = entry.getKey();
            Session session = entry.getValue();
            List<String> gaps = gaps(key, session, droppedRanges, rejections);
            gapRows.addAll(gaps);
            long first = session.received.first();
            long last = session.received.last();
            // 번호는 0 이상 long이라 처음 0, 끝 Long.MAX_VALUE면 기대 개수가 long을 넘는다. 간격(last - first)은 넘지 않으므로
            // 빠진 수는 간격으로 세고, 기대 개수는 부호 없는 수로 적는다.
            long span = last - first;
            long missing = span - (session.received.size() - 1);
            sessionRows.add(line(key.stream(), session.deviceId, key.sessionId(), first, last,
                    Long.toUnsignedString(span + 1), session.received.size(), missing,
                    Math.round(missing * 10000.0 / (span + 1.0)) / 10000.0,
                    gaps.size(), session.wsCount, session.restCount));
        }
        return new Lines(gapRows, sessionRows);
    }

    private static Map<Key, Session> sessions(List<SensorBatch> batches) {
        Map<Key, Session> sessions = new TreeMap<>(
                Comparator.comparing(Key::stream).thenComparing(Key::sessionId));
        for (SensorBatch batch : batches) {
            Session session = session(sessions, batch.getStream(), batch.getSessionId(), batch.getDeviceId());
            session.measured.put(batch.getSeq(), new long[] {batch.getMeasuredAtStartMs(), batch.getMeasuredAtEndMs()});
            session.received.add(batch.getSeq());
            if (SensorIngestChannel.WS.equals(batch.getIngestChannel())) {
                session.wsCount++;
            }
            if (SensorIngestChannel.REST.equals(batch.getIngestChannel())) {
                session.restCount++;
            }
            // 재전송은 원 세션의 원 번호를 채운다. 그 번호는 빠진 것이 아니고, 옆 공백의 경계 시각도 이 묶음이다(지시서 B4).
            // 원 묶음이 함께 있으면 그 시각을 남긴다.
            if (Boolean.TRUE.equals(batch.getIsResend()) && batch.getOriginalSessionId() != null
                    && batch.getOriginalSeq() != null) {
                Session original = session(sessions, batch.getStream(), batch.getOriginalSessionId(),
                        batch.getDeviceId());
                original.received.add(batch.getOriginalSeq());
                original.measured.putIfAbsent(batch.getOriginalSeq(),
                        new long[] {batch.getMeasuredAtStartMs(), batch.getMeasuredAtEndMs()});
            }
        }
        return sessions;
    }

    private static Session session(Map<Key, Session> sessions, String stream, String sessionId, String deviceId) {
        Session session = sessions.computeIfAbsent(new Key(stream, sessionId), key -> new Session());
        if (session.deviceId == null) {
            session.deviceId = deviceId;
        }
        return session;
    }

    private static List<String> gaps(Key key, Session session, List<SensorDroppedRange> droppedRanges,
                                     List<SensorBatchRejection> rejections) {
        List<String> rows = new ArrayList<>();
        Long previous = null;
        for (long seq : session.received) {
            if (previous != null && seq > previous + 1) {
                rows.add(gap(key, session, previous + 1, seq - 1, droppedRanges, rejections));
            }
            previous = seq;
        }
        return rows;
    }

    private static String gap(Key key, Session session, long from, long to,
                              List<SensorDroppedRange> droppedRanges, List<SensorBatchRejection> rejections) {
        // 기기 버림: 같은 세션·자료 종류의 구간을 빈 번호 구간으로 잘라 합친다.
        List<long[]> covered = new ArrayList<>();
        Set<String> reasons = new TreeSet<>();
        for (SensorDroppedRange range : droppedRanges) {
            if (!key.sessionId().equals(range.getSessionId()) || !key.stream().equals(range.getDroppedStream())
                    || range.getToSeq() < from || range.getFromSeq() > to) {
                continue;
            }
            covered.add(new long[] {Math.max(from, range.getFromSeq()), Math.min(to, range.getToSeq())});
            reasons.add(range.getReason());
        }
        List<long[]> merged = merge(covered);
        long droppedCount = 0;
        for (long[] interval : merged) {
            droppedCount += interval[1] - interval[0] + 1;
        }

        // 서버 거절: 같은 세션의 번호. 자료 종류를 읽었으면 그것도 같아야 한다.
        Set<Long> rejectedSeqs = new TreeSet<>();
        Set<String> codes = new TreeSet<>();
        Set<String> channels = new TreeSet<>();
        for (SensorBatchRejection rejection : rejections) {
            Long seq = rejection.getSeq();
            if (seq == null || seq < from || seq > to || !key.sessionId().equals(rejection.getSessionId())
                    || (rejection.getStream() != null && !key.stream().equals(rejection.getStream()))) {
                continue;
            }
            rejectedSeqs.add(seq);
            codes.add(rejection.getErrorCode());
            channels.add(rejection.getIngestChannel().name());
        }
        long rejectedOutsideDropped = 0;
        for (long seq : rejectedSeqs) {
            if (!contains(merged, seq)) {
                rejectedOutsideDropped++;
            }
        }

        long missing = to - from + 1;
        long unexplained = missing - droppedCount - rejectedOutsideDropped;
        Entry<Long, long[]> before = session.measured.lowerEntry(from);
        Entry<Long, long[]> after = session.measured.higherEntry(to);
        Long gapStartMs = null;
        Long gapEndMs = null;
        if (before != null) {
            gapStartMs = before.getValue()[1];
        }
        if (after != null) {
            gapEndMs = after.getValue()[0];
        }
        return line(key.stream(), session.deviceId, key.sessionId(), from, to, missing,
                gapStartMs, kstOrNull(gapStartMs), gapEndMs, kstOrNull(gapEndMs),
                droppedCount, String.join(";", reasons), rejectedSeqs.size(), String.join(";", codes),
                String.join(";", channels), unexplained, cause(droppedCount, rejectedSeqs.size(), unexplained));
    }

    private static String cause(long droppedCount, int rejectedCount, long unexplained) {
        List<String> causes = new ArrayList<>();
        if (droppedCount > 0) {
            causes.add(CAUSE_DEVICE_DROPPED);
        }
        if (rejectedCount > 0) {
            causes.add(CAUSE_SERVER_REJECTED);
        }
        if (unexplained > 0) {
            causes.add(CAUSE_NO_RECORD);
        }
        return String.join(";", causes);
    }

    private static List<long[]> merge(List<long[]> intervals) {
        intervals.sort(Comparator.comparingLong(interval -> interval[0]));
        List<long[]> merged = new ArrayList<>();
        for (long[] interval : intervals) {
            if (!merged.isEmpty() && interval[0] <= merged.get(merged.size() - 1)[1] + 1) {
                long[] last = merged.get(merged.size() - 1);
                last[1] = Math.max(last[1], interval[1]);
                continue;
            }
            merged.add(interval);
        }
        return merged;
    }

    private static boolean contains(List<long[]> intervals, long seq) {
        for (long[] interval : intervals) {
            if (seq >= interval[0] && seq <= interval[1]) {
                return true;
            }
        }
        return false;
    }

    private static String kstOrNull(Long epochMs) {
        if (epochMs == null) {
            return null;
        }
        return kst(epochMs);
    }
}
