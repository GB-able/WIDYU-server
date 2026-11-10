package com.widyu.incident.application;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.incident.IncidentKind;
import com.widyu.incident.IncidentResponseValue;
import com.widyu.incident.repository.IncidentAlertRecordRepository;
import com.widyu.incident.repository.IncidentAlertRow;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 알림(사건) 한 건마다 판정 근거와 어르신 응답을 한 줄로 모은 CSV를 만든다(LLD-0131).
 *
 * <p>심박 값·판정 사유가 들어가므로 로그에는 행 수만 남긴다(#639). 이름·전화번호는 싣지 않고 회원 ID만 싣는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentAlertExportService {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final String HEADER = line("alert_at_kst", "alert_at_ms", "incident_id", "member_id", "kind", "run_id",
            "detection_count", "decision_id", "severity", "hr_bpm", "hr_measured_at_ms", "hr_accuracy", "ai_reason",
            "on_body", "wear_state", "baseline_bpm", "held_seconds", "activity_state", "respond_by_ms",
            "senior_response", "response", "responded_at_ms", "response_via", "state", "guardian_alert_queued_at_ms",
            "guardian_push_delivered", "guardian_push_delivered_at_ms", "outcome");

    private static final long MAX_RANGE_MS = Duration.ofDays(31).toMillis();
    /** 서버 판정이 울린 알림만. 어르신이 직접 누른 도움 요청과 옛 안심구역 값은 뺀다(LLD-0131 3.1). */
    private static final List<IncidentKind> ALERT_KINDS = List.of(IncidentKind.HR_ANOMALY, IncidentKind.FALL_SUSPECTED);
    private static final DateTimeFormatter KST_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(KST);
    /** ADR-0042 이후에 채울 본인 기준값·이어진 시간·활동 상태. 칸 순서를 지키려고 비워 둔다(LLD-0131 5.3). */
    private static final String NOT_RECORDED_YET = null;

    private final IncidentAlertRecordRepository incidentAlertRecordRepository;

    public byte[] export(LocalDateTime from, LocalDateTime to) {
        long fromMs = from.atZone(KST).toInstant().toEpochMilli();
        long toMs = to.atZone(KST).toInstant().toEpochMilli();
        if (fromMs >= toMs || toMs - fromMs > MAX_RANGE_MS) {
            throw new BusinessException(ErrorCode.SENSOR_EXPORT_RANGE_INVALID);
        }
        List<IncidentAlertRow> rows = incidentAlertRecordRepository.findAlertRecords(fromMs, toMs, ALERT_KINDS);

        long nowMs = System.currentTimeMillis();
        StringBuilder csv = new StringBuilder(HEADER);
        for (IncidentAlertRow row : rows) {
            csv.append(row(row, nowMs));
        }
        log.info("알림 기록 내보내기: rows={}", rows.size());
        return csv.toString().getBytes(UTF_8);
    }

    static String row(IncidentAlertRow row, long nowMs) {
        return line(KST_FORMAT.format(Instant.ofEpochMilli(row.openedAtMs())), row.openedAtMs(), row.incidentRef(),
                row.memberId(), row.kind(), row.runId(), row.detectionCount(), row.decisionId(), row.level(),
                row.hrBpm(), row.hrMeasuredAtMs(), row.hrAccuracy(), row.reason(), row.onBody(), row.wearState(),
                NOT_RECORDED_YET, NOT_RECORDED_YET, NOT_RECORDED_YET, row.respondByMs(), seniorResponse(row, nowMs),
                row.response(), row.respondedAtMs(), row.responseVia(), row.state(), row.initialAlertSentAtMs(),
                row.alertDelivered(), row.alertAtMs(), row.outcome());
    }

    /**
     * 서버가 상태를 정하는 기준(LLD-0070 5.2)과 같다. 마감과 같거나 늦은 OK는 서버가 이미 무응답으로 보고 보호자
     * 단계로 올린 알림이라 {@code NO_RESPONSE}다. 실제 보낸 값은 {@code response} 칸에 남는다.
     */
    static String seniorResponse(IncidentAlertRow row, long nowMs) {
        if (row.response() == IncidentResponseValue.HELP) {
            return "HELP";
        }
        if (row.response() == IncidentResponseValue.OK && row.respondedAtMs() < row.respondByMs()) {
            return "OK";
        }
        if (row.response() == null && nowMs < row.respondByMs()) {
            return "PENDING";
        }
        return "NO_RESPONSE";
    }

    static String line(Object... values) {
        StringBuilder line = new StringBuilder();
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                line.append(',');
            }
            line.append(escape(values[index]));
        }
        return line.append('\n').toString();
    }

    // ponytail: SensorWindowCsv.escape와 같은 규칙의 사본. 그 파일은 열린 PR #988·#992가 고치는 중이라 손대지 않았다.
    // 두 PR이 합쳐지면 공용 CSV 유틸 하나로 모은다.
    /** 스프레드시트가 수식으로 읽는 문자로 시작하는 문자열은 앞에 작은따옴표를 붙인다. AI 사유 원문이 들어가기 때문이다. */
    private static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (value instanceof CharSequence && startsLikeFormula(text)) {
            text = "'" + text;
        }
        if (text.indexOf(',') < 0 && text.indexOf('"') < 0 && text.indexOf('\n') < 0 && text.indexOf('\r') < 0) {
            return text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    private static boolean startsLikeFormula(String text) {
        if (text.isEmpty()) {
            return false;
        }
        char first = text.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r';
    }
}
