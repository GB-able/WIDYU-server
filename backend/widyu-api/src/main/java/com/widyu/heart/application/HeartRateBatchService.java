package com.widyu.heart.application;

import com.widyu.decision.DecisionRecord;
import com.widyu.decision.application.DecisionRecordPersistenceService;
import com.widyu.global.error.BusinessException;
import com.widyu.global.properties.SensorProperties;
import com.widyu.heart.HeartRateStatus;
import com.widyu.heart.application.HeartRateAnomalyDetector.DetectionResult;
import com.widyu.heart.dto.request.HeartRateMeasurement;
import com.widyu.heart.repository.HeartRateEventRepository;
import com.widyu.member.Member;
import com.widyu.sensor.application.ActivityStateService;
import com.widyu.sensor.dto.request.HeartRateBatchRequest;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongFunction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * 심박 배치의 샘플을 저장하고 판정한다(LLD-0047 5절 6단계, ADR-0031 결정 3).
 *
 * <p><b>저장이 판정에 앞선다.</b> 값이 이상해도 저장하고 제외는 판정 단계에서 한다(정책 1.2.1).
 * AI가 실패하면 그 샘플을 {@code UNKNOWN}으로 저장하고 넘어가며, 배치 안에서 한 번 실패하면 남은
 * 샘플은 AI를 건너뛴다. 응답 지연이 샘플 수만큼 곱해져 수신 스레드가 잠기는 것을 막기 위해서다.
 *
 * <p>알림이 나간 건과 판정 못 한 건만 {@code decision_record}에 남긴다(ADR-0035 결정 1). 정상·주의
 * 판정은 행을 만들지 않고 메트릭으로 건수만 센다. 하루 8만 행을 만들 이유가 없고, 정상 판정은
 * {@code heart_rate_event.status}에 이미 있다.
 *
 * <p>착용이 확실하지 않은 배치는 저장만 하고 판정하지 않는다(LLD-0126). 스위치
 * {@code sensor.heart-ai.wear-gate-enabled}가 켜져 있을 때만이다.
 *
 * <p>AI {@code context}는 스위치 {@code sensor.heart-ai.activity-context-enabled}가 켜져 있을 때만 샘플 측정 시각의
 * 활동 상태를 보내고, 꺼져 있으면 {@code UNKNOWN}이다(LLD-0138). 지금 AI는 {@code REST}에서 고정 기준을 끄므로
 * 노션 3-1 배포와 검증 전에는 켜지 않는다.
 *
 * <p>심박 수치·판정 상태·판정 사유·AI 응답은 <b>어떤 로그 레벨에도 남기지 않는다</b>(#639).
 * 사유와 심박 값이 사는 곳은 판정 기록 행뿐이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HeartRateBatchService {

    private static final ZoneId HEART_RATE_ZONE = ZoneId.of("Asia/Seoul");
    private static final String AI_CONTEXT = "UNKNOWN";
    private static final String ACCURACY_UNRELIABLE = "UNRELIABLE";
    private static final String ALERT = "ALERT";
    private static final String NO_ALERT = "NO_ALERT";
    private static final String ABSTAIN = "ABSTAIN_INSUFFICIENT_INPUT";
    private static final String WEAR_UNCERTAIN = "WEAR_UNCERTAIN";
    private static final Set<String> UNCERTAIN_WEAR_STATES =
            Set.of("UNKNOWN", "WORN_INVALID", "PLANNED_NOT_WORN", "ACTUAL_NOT_WORN");
    private static final String DECISION_ID_UK = "uk_decision_record_decision_id";
    // AI가 0과 300을 400으로 거부한다.
    private static final int AI_MIN_BPM = 1;
    private static final int AI_MAX_BPM = 299;

    private final HeartRateAnomalyDetector heartRateAnomalyDetector;
    private final HeartRatePersistenceService heartRatePersistenceService;
    private final HeartRateEventRepository heartRateEventRepository;
    private final DecisionRecordPersistenceService decisionRecordPersistenceService;
    private final SensorProperties sensorProperties;
    private final MeterRegistry meterRegistry;
    private final ActivityStateService activityStateService;

    /** 저장 건수·스킵 건수·AI를 거치지 않은 건수. 로그에 쓴다(값은 담지 않는다). */
    public record BatchOutcome(int stored, int skipped, int aiSkipped) {}

    public BatchOutcome storeAndAssess(
            Member member, String batchId, String runId, Boolean onBody, String wearState,
            List<HeartRateBatchRequest.Sample> samples, long serverReceivedAtMs) {
        boolean wearUncertain = isWearUncertain(onBody, wearState);
        LongFunction<String> aiContext = aiContext(member.getId(), samples, wearUncertain);
        boolean aiAvailable = true;
        boolean aiCalled = false;
        int stored = 0;
        int skipped = 0;
        int raceSkipped = 0;
        int aiSkipped = 0;
        int aiTargetCount = 0;

        Integer lastHeartRate = null;
        LocalDateTime lastMeasuredAt = null;
        HeartRateStatus lastStatus = null;

        for (HeartRateBatchRequest.Sample sample : sortedByMeasuredTime(samples)) {
            LocalDateTime measuredAt = toMeasuredAt(sample.tsMs());
            // 재전송이나 단건 경로로 이미 들어온 시각은 건너뛴다. UK (member_id, measured_at)와 같은 판정이다.
            if (heartRateEventRepository.existsByMemberIdAndMeasuredAt(member.getId(), measuredAt)) {
                skipped++;
                continue;
            }

            HeartRateStatus status = HeartRateStatus.UNKNOWN;
            boolean emergency = false;
            boolean assessed = false;
            DecisionRecord decision = null;
            if (!wearUncertain && aiAvailable && isAiTarget(sample)) {
                aiCalled = true;
                try {
                    DetectionResult detected = heartRateAnomalyDetector.detect(
                            member.getId(), HeartRateMeasurement.of(sample.bpm(), measuredAt),
                            aiContext.apply(sample.tsMs()));
                    status = detected.status();
                    emergency = detected.emergency();
                    assessed = true;
                    aiTargetCount++;
                    decision = alertDecision(member, batchId, runId, sample, detected, serverReceivedAtMs);
                } catch (RestClientException | BusinessException e) {
                    // 판정 누락이 아니라 판정 불가의 기록이다. 남은 샘플은 AI를 부르지 않는다.
                    aiAvailable = false;
                }
            }

            // 판정 행은 여기서 심박 이벤트·위급·알림 발행과 한 트랜잭션으로 저장된다. 따로 커밋하면
            // 뒤이은 심박 저장이 실패했을 때 알림 없이 판정만 남는다(LLD-0053 5.1).
            if (!saveUnlessConcurrentlyStored(member, sample, measuredAt, status, emergency, batchId, decision)) {
                raceSkipped++;
                continue;
            }
            if (!assessed) {
                aiSkipped++;
            }
            // 저장된 샘플만 센다. 동시 재전송이 먼저 저장한 샘플까지 세면 같은 판정이 두 번 집계된다(LLD-0089).
            if (assessed && !emergency) {
                meterRegistry.counter("heart.decision", "output", NO_ALERT).increment();
            }
            if (decision != null) {
                log.info("심박 판정 기록: memberId={}, decisionId={}, output={}, batchId={}",
                        member.getId(), decision.getDecisionId(), decision.getDecisionOutput(), batchId);
            }
            stored++;
            lastHeartRate = sample.bpm();
            lastMeasuredAt = measuredAt;
            lastStatus = status;
        }

        if (raceSkipped > 0) {
            log.warn("심박 동시 재전송 샘플 건너뜀: memberId={}, batchId={}, count={}",
                    member.getId(), batchId, raceSkipped);
        }
        if (lastMeasuredAt != null) {
            heartRatePersistenceService.updateLatestResult(
                    member.getId(), lastStatus, lastHeartRate, lastMeasuredAt);
        }
        if (wearUncertain && stored > 0) {
            // 회차가 없는 배치는 판정 행이 없으므로 이 카운터가 유일한 관측 수단이다(LLD-0126 5.2).
            // heart.decision은 output 태그만 쓴다. 같은 이름에 태그 키가 다르면 Prometheus가 한쪽을 버려 이름을 나눈다.
            meterRegistry.counter("heart.wear_uncertain").increment();
        }
        String abstainReason = null;
        if (wearUncertain) {
            abstainReason = WEAR_UNCERTAIN;
        }
        recordAbstain(member, batchId, runId, samples, stored, aiTargetCount, aiCalled, abstainReason,
                serverReceivedAtMs);
        return new BatchOutcome(stored, skipped + raceSkipped, aiSkipped);
    }

    /**
     * 샘플을 저장하고, 같은 배치를 동시에 처리하던 요청이 이 시각을 먼저 저장했으면 false를 돌려준다.
     *
     * <p>위의 존재 조회와 이 저장 사이에 다른 요청이 끼어들면 UK (member_id, measured_at) 충돌로만
     * 드러난다(LLD-0089). 위반한 제약이 그 UK이고 행이 실제로 있을 때만 건너뛴다. 판정·위급 행의 다른 제약
     * 위반은 그 사이 심박 행이 생겼어도 그대로 던진다.
     * 이 메서드는 트랜잭션 밖이고 {@code saveBatchSample}이 자기 트랜잭션이라 예외가 호출 지점에서 난다.
     */
    private boolean saveUnlessConcurrentlyStored(
            Member member, HeartRateBatchRequest.Sample sample, LocalDateTime measuredAt,
            HeartRateStatus status, boolean emergency, String batchId, DecisionRecord decision) {
        try {
            heartRatePersistenceService.saveBatchSample(
                    member, sample.bpm(), measuredAt, status, emergency, sample.accuracy(), batchId, decision);
            return true;
        } catch (DataIntegrityViolationException e) {
            if (HeartEventConflict.storedByConcurrentRequest(e, heartRateEventRepository, member.getId(), measuredAt)) {
                return false;
            }
            throw e;
        }
    }

    /**
     * 위급이면 판정 행 하나를 조립한다. 그 밖이면 저장 뒤 건수만 센다(ADR-0035 결정 1).
     *
     * <p>저장은 하지 않는다. 심박 저장과 한 트랜잭션에 묶으려고 엔티티만 만들어 넘긴다.
     * 조립이 실패하면(설정 누락 등) 판정 없이 심박만 저장한다.
     */
    private DecisionRecord alertDecision(
            Member member, String batchId, String runId, HeartRateBatchRequest.Sample sample,
            DetectionResult detected, long serverReceivedAtMs) {
        if (!detected.emergency()) {
            return null;
        }
        if (runId == null) {
            return null;
        }
        try {
            return alertRecord(member, batchId, runId, sample, detected, serverReceivedAtMs);
        } catch (Exception e) {
            log.warn("심박 판정 기록 실패: memberId={}, batchId={}, errorType={}",
                    member.getId(), batchId, e.getClass().getSimpleName());
            return null;
        }
    }

    private DecisionRecord alertRecord(
            Member member, String batchId, String runId, HeartRateBatchRequest.Sample sample,
            DetectionResult detected, long serverReceivedAtMs) {
        // 인과성: 기기 시계가 서버보다 앞서면 feature_support_end_ms > input_cutoff_ms가 된다.
        // 낙상과 달리 창을 서버가 정하지 않고 샘플 시각이 곧 창이라, 막지 않고 그대로 적어 검사기가 신고하게 둔다(ADR-0035).
        return DecisionRecord.builder()
                .decisionId(newDecisionId())
                .memberId(member.getId())
                .runId(runId)
                .streamIdsUsed(streamIds(batchId))
                .decisionAtMs(System.currentTimeMillis())
                .decisionOutput(ALERT)
                .deciderId(sensorProperties.heartAi().deciderId())
                .deciderVersion(sensorProperties.heartAi().deciderVersion())
                .inputCutoffMs(serverReceivedAtMs)
                // AI 내부 이력은 서버가 모른다. 판정이 실제로 딛고 선 자료는 이 샘플 한 점이다.
                .featureSupportEndMs(sample.tsMs())
                .modelAvailableAtServerMaxMs(serverReceivedAtMs)
                .windowStartMs(sample.tsMs())
                .windowEndMs(sample.tsMs())
                .severity(detected.level())
                .triggerBatchId(batchId)
                .hrBpm(sample.bpm())
                .hrMeasuredAtMs(sample.tsMs())
                .hrAccuracy(sample.accuracy())
                .reason(detected.reason())
                .build();
    }

    /**
     * AI에 넘길 샘플이 하나도 없던 배치는 「판정하지 않음」을 배치 단위로 한 줄 남긴다.
     *
     * <p>세 가지를 제외한다. AI 호출이 실패해서 대상이 0이 된 경우는 입력 부족이 아니라 판정기
     * 장애다(ADR-0033 결정 2). 중복으로 건너뛴 샘플도 세지 않는다 — 이미 판정한 시각이 다시 온
     * 것이라 입력이 부족한 것이 아니다. 그래서 <b>전부 중복인 재전송 배치는 아무 행도 남기지 않는다</b>.
     * 남겼다면 재전송 횟수만큼 없던 「판정 불가」가 쌓여 사후 분석이 오염된다.
     *
     * <p>{@code decision_id}는 배치에서 정한다. 같은 배치를 WS와 REST가 동시에 처리하며 샘플 저장이 나뉘면 두 요청
     * 모두 이 행을 만들려 하는데, 두 번째는 UK 충돌로 끝난다(LLD-0126 5.2).
     */
    private void recordAbstain(
            Member member, String batchId, String runId, List<HeartRateBatchRequest.Sample> samples,
            int assessableCount, int aiTargetCount, boolean aiCalled, String reason, long serverReceivedAtMs) {
        if (runId == null || assessableCount == 0 || aiTargetCount > 0 || aiCalled) {
            return;
        }
        List<HeartRateBatchRequest.Sample> sorted = sortedByMeasuredTime(samples);
        long firstTsMs = sorted.get(0).tsMs();
        long lastTsMs = sorted.get(sorted.size() - 1).tsMs();
        save(DecisionRecord.builder()
                .decisionId(abstainDecisionId(batchId))
                .memberId(member.getId())
                .runId(runId)
                .streamIdsUsed(streamIds(batchId))
                .decisionAtMs(System.currentTimeMillis())
                .decisionOutput(ABSTAIN)
                .deciderId(sensorProperties.fallAi().serverDeciderId())
                .deciderVersion(sensorProperties.fallAi().serverDeciderVersion())
                .inputCutoffMs(serverReceivedAtMs)
                .featureSupportEndMs(lastTsMs)
                .modelAvailableAtServerMaxMs(serverReceivedAtMs)
                .windowStartMs(firstTsMs)
                .windowEndMs(lastTsMs)
                .triggerBatchId(batchId)
                .reason(reason)
                .build(), member.getId(), batchId);
    }

    /**
     * 배치 단위 판정 보류만 이 길로 저장한다. 짝이 될 심박 이벤트도 알림도 없어 묶을 트랜잭션이
     * 없으므로 자기 트랜잭션(REQUIRES_NEW)에서 끝낸다. 실패해도 심박 저장을 막지 않는다.
     * 같은 {@code decision_id}가 이미 있으면 동시 재전송이 먼저 남긴 것이라 실패로 보지 않는다.
     */
    private void save(DecisionRecord record, Long memberId, String batchId) {
        try {
            DecisionRecord saved = decisionRecordPersistenceService.save(record);
            log.info("심박 판정 기록: memberId={}, decisionId={}, output={}, batchId={}",
                    memberId, saved.getDecisionId(), saved.getDecisionOutput(), batchId);
        } catch (DataIntegrityViolationException e) {
            if (!violatesDecisionIdUk(e)) {
                log.warn("심박 판정 기록 실패: memberId={}, batchId={}, errorType={}",
                        memberId, batchId, e.getClass().getSimpleName());
                return;
            }
            log.info("심박 판정 기록 중복: memberId={}, decisionId={}, batchId={}",
                    memberId, record.getDecisionId(), batchId);
        } catch (Exception e) {
            log.warn("심박 판정 기록 실패: memberId={}, batchId={}, errorType={}",
                    memberId, batchId, e.getClass().getSimpleName());
        }
    }

    private String newDecisionId() {
        return "dec-" + UUID.randomUUID().toString().replace("-", "");
    }

    /** 배치당 한 줄이라 배치에서 정한다. {@code batch_id}는 26자라 33자로 컬럼 40자 안이다. */
    private String abstainDecisionId(String batchId) {
        return "dec-ab-" + batchId;
    }

    /**
     * 제약 이름은 Hibernate가 DB 메시지에서 뽑은 값이라 DB마다 모양이 달라 포함 여부로 본다
     * ({@link HeartEventConflict}와 같은 방식).
     */
    private boolean violatesDecisionIdUk(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof ConstraintViolationException violation
                    && violation.getConstraintName() != null
                    && violation.getConstraintName().toLowerCase(Locale.ROOT).contains(DECISION_ID_UK)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 샘플 측정 시각마다 AI에 보낼 활동 상태. 스위치가 꺼져 있거나 AI를 부르지 않는 배치면 조회 없이 {@code UNKNOWN}이다.
     * 조회가 실패해도 심박 저장과 판정을 막지 않고 {@code UNKNOWN}으로 보낸다(LLD-0138 6절).
     */
    private LongFunction<String> aiContext(
            Long memberId, List<HeartRateBatchRequest.Sample> samples, boolean wearUncertain) {
        LongFunction<String> unknown = tsMs -> AI_CONTEXT;
        if (wearUncertain || sensorProperties.heartAi() == null
                || !sensorProperties.heartAi().activityContextEnabled()) {
            return unknown;
        }
        List<HeartRateBatchRequest.Sample> sorted = sortedByMeasuredTime(samples);
        try {
            return activityStateService.contexts(
                    memberId, sorted.get(0).tsMs(), sorted.get(sorted.size() - 1).tsMs());
        } catch (Exception e) {
            log.warn("활동 상태 조회 실패: memberId={}, errorType={}", memberId, e.getClass().getSimpleName());
            return unknown;
        }
    }

    /**
     * 착용이 확실하지 않으면 그 배치는 판정하지 않는다(LLD-0126 5.1). 착용 상태가 없으면(워치 #110 전)
     * {@code on_body}만 본다.
     */
    private boolean isWearUncertain(Boolean onBody, String wearState) {
        // 설정이 빠졌으면 판정 행 조립처럼 심박 저장을 막지 않고 게이트를 끈 것으로 본다.
        if (sensorProperties.heartAi() == null || !sensorProperties.heartAi().wearGateEnabled()) {
            return false;
        }
        if (!Boolean.TRUE.equals(onBody)) {
            return true;
        }
        return wearState != null && UNCERTAIN_WEAR_STATES.contains(wearState);
    }

    /** {@code batch_id}는 수신 검증이 소문자 ULID 26자로 좁혀 두므로 JSON 이스케이프할 문자가 없다. */
    private String streamIds(String batchId) {
        return "[\"%s\"]".formatted(batchId);
    }

    /** AI는 1..299가 아닌 값과 신뢰할 수 없는 샘플을 판정하지 않는다. 저장은 그래도 한다. */
    private boolean isAiTarget(HeartRateBatchRequest.Sample sample) {
        if (ACCURACY_UNRELIABLE.equals(sample.accuracy())) {
            return false;
        }
        return sample.bpm() >= AI_MIN_BPM && sample.bpm() <= AI_MAX_BPM;
    }

    private List<HeartRateBatchRequest.Sample> sortedByMeasuredTime(
            List<HeartRateBatchRequest.Sample> samples) {
        return samples.stream()
                .sorted(Comparator.comparing(HeartRateBatchRequest.Sample::tsMs))
                .toList();
    }

    /** 기존 그래프·위급 사이클·30일 정리와 같은 시간축을 쓴다. 원 시각은 S3 원문과 인덱스 행에 남는다. */
    private LocalDateTime toMeasuredAt(long tsMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(tsMs), HEART_RATE_ZONE);
    }
}
