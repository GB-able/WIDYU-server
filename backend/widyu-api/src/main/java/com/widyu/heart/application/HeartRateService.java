package com.widyu.heart.application;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.fcm.event.heart.dto.HeartRateEmergencyEvent;
import com.widyu.heart.application.HeartRateAnomalyDetector.DetectionResult;
import com.widyu.heart.HeartRateEmergency;
import com.widyu.heart.HeartRateEvent;
import com.widyu.heart.HeartRateResult;
import com.widyu.heart.dto.request.HeartRateMeasurement;
import com.widyu.heart.dto.request.HeartRateSendRequest;
import com.widyu.heart.dto.response.EmergencyEventResponse;
import com.widyu.heart.dto.response.EmergencyHistoryResponse;
import com.widyu.heart.dto.response.HeartGraphCurrentResponse;
import com.widyu.heart.dto.response.HeartGraphPageResponse;
import com.widyu.heart.dto.response.HeartGraphResponse;
import com.widyu.heart.dto.response.HeartRateEventResponse;
import com.widyu.heart.dto.response.HeartRateStatusResponse;
import com.widyu.heart.dto.response.RecentEmergencyResponse;
import com.widyu.heart.repository.HeartRateEmergencyRepository;
import com.widyu.heart.repository.HeartRateEventRepository;
import com.widyu.heart.repository.HeartRateResultRepository;
import com.widyu.member.repository.MemberRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HeartRateService {

    /** 사이클 시작 이전으로 더 보여줄 구간. 이상해지기 직전의 정상 심박을 함께 보여주기 위한 여유다. */
    private static final Duration GRAPH_LEAD_IN = Duration.ofMinutes(5);

    /**
     * 위급상황 사이클 유지 시간. 위험이 감지되면 그 시점부터 이 시간만큼 위험 상태를 유지하고,
     * 그 안에 다시 감지되면 마지막 감지 시각 기준으로 연장된다.
     * "마지막 감지 + 5분"과 "최근 5분 내 감지 존재"는 동치이므로 사이클 상태를 따로 저장하지 않는다.
     */
    private static final Duration EMERGENCY_CYCLE_WINDOW = Duration.ofMinutes(5);

    private final HeartRateAnomalyDetector heartRateAnomalyDetector;
    private final HeartRatePersistenceService heartRatePersistenceService;
    private final ApplicationEventPublisher eventPublisher;
    private final HeartRateResultRepository heartRateResultRepository;
    private final HeartRateEventRepository heartRateEventRepository;
    private final HeartRateEmergencyRepository heartRateEmergencyRepository;
    private final MemberRepository memberRepository;

    public HeartRateStatusResponse processHeartRates(Long memberId, HeartRateSendRequest request) {
        validateMemberExists(memberId);

        LocalDateTime batchStart = request.heartRates().stream()
                .map(HeartRateMeasurement::measuredAt)
                .min(Comparator.naturalOrder())
                .orElseThrow();

        if (heartRateEventRepository.existsByMemberIdAndMeasuredAt(memberId, batchStart)) {
            return getHeartRateStatus(memberId);
        }

        DetectionResult detection = heartRateAnomalyDetector.detect(
                memberId,
                request.heartRates(),
                request.normalizedContext()
        );

        HeartRateResult result = heartRatePersistenceService.saveAnalysis(
                memberId,
                request,
                detection.status(),
                detection.emergency()
        );

        if (detection.emergency()) {
            eventPublisher.publishEvent(new HeartRateEmergencyEvent(memberId));
        }

        // rawContext는 앱이 context를 실제로 보내는지 확인하기 위한 값이다 (미전송이면 null·공백, LLD-0019)
        log.info("심박수 분석 완료: memberId={}, status={}, heartRate={}, measuredAt={}, rawContext=[{}]",
                memberId, detection.status(), result.getHeartRate(), result.getMeasuredAt(), request.context());

        return HeartRateStatusResponse.from(result);
    }

    public HeartRateStatusResponse getHeartRateStatus(Long memberId) {
        return heartRateResultRepository.findByMemberId(memberId)
                .map(HeartRateStatusResponse::from)
                .or(() -> heartRateEventRepository.findFirstByMemberIdOrderByMeasuredAtDesc(memberId)
                        .map(event -> HeartRateStatusResponse.from(memberId, event)))
                .orElse(HeartRateStatusResponse.unknown(memberId));
    }

    @Transactional(readOnly = true)
    public RecentEmergencyResponse getRecentEmergency(Long memberId) {
        LocalDateTime since = LocalDateTime.now().minus(EMERGENCY_CYCLE_WINDOW);
        return heartRateEmergencyRepository
                .findFirstByMemberIdAndMeasuredAtAfterOrderByMeasuredAtDesc(memberId, since)
                .map(emergency -> RecentEmergencyResponse.from(
                        emergency, emergency.getMeasuredAt().plus(EMERGENCY_CYCLE_WINDOW)))
                .orElseGet(RecentEmergencyResponse::notDetected);
    }

    @Transactional(readOnly = true)
    public HeartGraphPageResponse getHeartGraph(Long memberId) {
        List<HeartRateEmergency> emergencies = heartRateEmergencyRepository.findByMemberIdOrderByMeasuredAtDesc(memberId);
        LocalDateTime windowStart = findGraphWindowStart(emergencies);

        if (windowStart == null) {
            return emptyGraph(memberId, HeartGraphResponse::forInitialEmpty, emergencies);
        }

        List<HeartRateEventResponse> events = heartRateEventRepository
                .findByMemberIdAndMeasuredAtAfterOrderByMeasuredAtAsc(memberId, windowStart)
                .stream()
                .map(HeartRateEventResponse::from)
                .toList();

        HeartRateEventResponse firstEmergency = heartRateEmergencyRepository
                .findFirstByMemberIdAndMeasuredAtAfterOrderByMeasuredAtAsc(memberId, windowStart)
                .map(HeartRateEventResponse::from)
                .orElse(null);

        HeartGraphResponse heartGraph = HeartGraphResponse.forInitial(
                buildCurrent(memberId, windowStart), firstEmergency, events);

        return HeartGraphPageResponse.of(heartGraph, buildEmergencyHistory(memberId, emergencies));
    }

    @Transactional(readOnly = true)
    public HeartGraphPageResponse getHeartGraphRefresh(Long memberId, LocalDateTime since) {
        List<HeartRateEmergency> emergencies = heartRateEmergencyRepository.findByMemberIdOrderByMeasuredAtDesc(memberId);
        LocalDateTime windowStart = findGraphWindowStart(emergencies);

        if (windowStart == null) {
            return emptyGraph(memberId, HeartGraphResponse::forRefreshEmpty, emergencies);
        }

        List<HeartRateEventResponse> events = findRefreshEvents(memberId, since, windowStart)
                .stream()
                .map(HeartRateEventResponse::from)
                .toList();

        HeartGraphResponse heartGraph = HeartGraphResponse.forRefresh(buildCurrent(memberId, windowStart), events);

        return HeartGraphPageResponse.of(heartGraph, buildEmergencyHistory(memberId, emergencies));
    }

    /**
     * 그래프는 진행 중인 위급 사이클을 보여주는 화면이므로, 사이클 시작 시각에서
     * {@link #GRAPH_LEAD_IN}만큼 앞선 지점을 조회 시작점으로 쓴다. 이상해지기 직전의 정상 구간을
     * 함께 보여주기 위해서다. 진행 중인 사이클이 없으면 null을 반환해 빈 그래프로 응답한다.
     */
    private LocalDateTime findGraphWindowStart(List<HeartRateEmergency> emergenciesDesc) {
        LocalDateTime cycleStart = findActiveCycleStart(emergenciesDesc);
        if (cycleStart == null) {
            return null;
        }
        return cycleStart.minus(GRAPH_LEAD_IN);
    }

    /**
     * 최신 기록부터 과거로 훑으며 간격이 {@link #EMERGENCY_CYCLE_WINDOW} 이내로 이어지는 구간의
     * 첫 감지 시각을 찾는다. 가장 최근 감지가 이미 만료됐으면 진행 중인 사이클이 없다.
     */
    private LocalDateTime findActiveCycleStart(List<HeartRateEmergency> emergenciesDesc) {
        LocalDateTime expiredBefore = LocalDateTime.now().minus(EMERGENCY_CYCLE_WINDOW);
        LocalDateTime cycleStart = null;
        LocalDateTime newer = null;

        for (HeartRateEmergency emergency : emergenciesDesc) {
            LocalDateTime measuredAt = emergency.getMeasuredAt();
            if (newer == null && !measuredAt.isAfter(expiredBefore)) {
                return null;
            }
            if (newer != null && newer.minus(EMERGENCY_CYCLE_WINDOW).isAfter(measuredAt)) {
                break;
            }
            cycleStart = measuredAt;
            newer = measuredAt;
        }
        return cycleStart;
    }

    private HeartGraphPageResponse emptyGraph(
            Long memberId,
            Function<HeartGraphCurrentResponse, HeartGraphResponse> graphFactory,
            List<HeartRateEmergency> emergencies
    ) {
        HeartGraphResponse heartGraph = graphFactory.apply(buildCurrentWithoutRange(memberId));
        return HeartGraphPageResponse.of(heartGraph, buildEmergencyHistory(memberId, emergencies));
    }

    /**
     * since 미지정(기존 클라이언트)은 최근 5개, 지정 시 그 이후 신규 이벤트만 반환한다.
     * since가 그래프 조회 범위보다 과거면 범위 시작점으로 잘라낸다.
     */
    private List<HeartRateEvent> findRefreshEvents(Long memberId, LocalDateTime since, LocalDateTime windowStart) {
        if (since == null) {
            return heartRateEventRepository.findTop5ByMemberIdOrderByMeasuredAtDesc(memberId)
                    .stream()
                    .sorted(Comparator.comparing(HeartRateEvent::getMeasuredAt))
                    .toList();
        }
        if (since.isBefore(windowStart)) {
            return heartRateEventRepository.findByMemberIdAndMeasuredAtAfterOrderByMeasuredAtAsc(memberId, windowStart);
        }
        return heartRateEventRepository.findByMemberIdAndMeasuredAtAfterOrderByMeasuredAtAsc(memberId, since);
    }

    private HeartGraphCurrentResponse buildCurrent(Long memberId, LocalDateTime windowStart) {
        Integer maxHeartRate = heartRateEventRepository.findMaxHeartRateByMemberIdSince(memberId, windowStart).orElse(null);
        Integer minHeartRate = heartRateEventRepository.findMinHeartRateByMemberIdSince(memberId, windowStart).orElse(null);
        return buildCurrent(memberId, maxHeartRate, minHeartRate);
    }

    private HeartGraphCurrentResponse buildCurrentWithoutRange(Long memberId) {
        return buildCurrent(memberId, null, null);
    }

    private HeartGraphCurrentResponse buildCurrent(Long memberId, Integer maxHeartRate, Integer minHeartRate) {
        HeartRateResult latest = heartRateResultRepository.findByMemberId(memberId).orElse(null);
        if (latest != null) {
            return HeartGraphCurrentResponse.of(
                    latest.getHeartRate(), latest.getMeasuredAt(), maxHeartRate, minHeartRate, latest.getStatus());
        }

        HeartRateEvent latestEvent = heartRateEventRepository
                .findFirstByMemberIdOrderByMeasuredAtDesc(memberId)
                .orElse(null);
        if (latestEvent == null) {
            return HeartGraphCurrentResponse.unknown(maxHeartRate, minHeartRate);
        }
        return HeartGraphCurrentResponse.of(
                latestEvent.getHeartRate(),
                latestEvent.getMeasuredAt(),
                maxHeartRate,
                minHeartRate,
                latestEvent.getStatus()
        );
    }

    private void validateMemberExists(Long memberId) {
        memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private EmergencyHistoryResponse buildEmergencyHistory(Long memberId, List<HeartRateEmergency> emergenciesDesc) {
        int totalDuration = heartRateEventRepository.findTotalDurationMinutesByMemberId(memberId).orElse(0);
        List<EmergencyEventResponse> emergencyEvents = emergenciesDesc.stream()
                .map(EmergencyEventResponse::from)
                .toList();
        return EmergencyHistoryResponse.of(emergenciesDesc.size(), totalDuration, emergencyEvents);
    }
}
