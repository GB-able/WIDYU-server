package com.widyu.heart.application;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.fcm.event.heart.dto.HeartRateEmergencyEvent;
import com.widyu.heart.application.HeartRateAnomalyDetector.DetectionResult;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HeartRateService {

    private static final Duration GRAPH_WINDOW = Duration.ofHours(24);
    private static final Duration RECENT_EMERGENCY_WINDOW = Duration.ofMinutes(15);

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

        log.info("심박수 분석 완료: memberId={}, status={}, heartRate={}, measuredAt={}",
                memberId, detection.status(), result.getHeartRate(), result.getMeasuredAt());

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
        LocalDateTime since = LocalDateTime.now().minus(RECENT_EMERGENCY_WINDOW);
        return heartRateEmergencyRepository
                .findFirstByMemberIdAndMeasuredAtAfterOrderByMeasuredAtDesc(memberId, since)
                .map(RecentEmergencyResponse::from)
                .orElseGet(RecentEmergencyResponse::notDetected);
    }

    @Transactional(readOnly = true)
    public HeartGraphPageResponse getHeartGraph(Long memberId) {
        LocalDateTime windowStart = graphWindowStart();

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

        return HeartGraphPageResponse.of(heartGraph, buildEmergencyHistory(memberId));
    }

    @Transactional(readOnly = true)
    public HeartGraphPageResponse getHeartGraphRefresh(Long memberId, LocalDateTime since) {
        LocalDateTime windowStart = graphWindowStart();

        List<HeartRateEventResponse> events = findRefreshEvents(memberId, since, windowStart)
                .stream()
                .map(HeartRateEventResponse::from)
                .toList();

        HeartGraphResponse heartGraph = HeartGraphResponse.forRefresh(buildCurrent(memberId, windowStart), events);

        return HeartGraphPageResponse.of(heartGraph, buildEmergencyHistory(memberId));
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

    private LocalDateTime graphWindowStart() {
        return LocalDateTime.now().minus(GRAPH_WINDOW);
    }

    private void validateMemberExists(Long memberId) {
        memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private EmergencyHistoryResponse buildEmergencyHistory(Long memberId) {
        long emergencyCount = heartRateEmergencyRepository.countByMemberId(memberId);
        int totalDuration = heartRateEventRepository.findTotalDurationMinutesByMemberId(memberId).orElse(0);
        List<EmergencyEventResponse> emergencyEvents = heartRateEmergencyRepository
                .findByMemberIdOrderByMeasuredAtDesc(memberId)
                .stream()
                .map(EmergencyEventResponse::from)
                .toList();
        return EmergencyHistoryResponse.of(emergencyCount, totalDuration, emergencyEvents);
    }
}
