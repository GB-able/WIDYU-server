package com.widyu.heart.application;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.fcm.event.heart.dto.HeartRateEmergencyEvent;
import com.widyu.heart.application.HeartRateAnomalyDetector.DetectionResult;
import com.widyu.heart.HeartRateEmergency;
import com.widyu.heart.HeartRateEvent;
import com.widyu.heart.HeartRateResult;
import com.widyu.heart.HeartRateStatus;
import com.widyu.heart.dto.request.HeartRateMeasurement;
import com.widyu.heart.dto.request.HeartRateSendRequest;
import com.widyu.heart.dto.response.EmergencyEventResponse;
import com.widyu.heart.dto.response.EmergencyHistoryResponse;
import com.widyu.heart.dto.response.HeartGraphCurrentResponse;
import com.widyu.heart.dto.response.HeartGraphPageResponse;
import com.widyu.heart.dto.response.HeartGraphResponse;
import com.widyu.heart.dto.response.HeartRateEventResponse;
import com.widyu.heart.dto.response.HeartRateStatusResponse;
import com.widyu.heart.repository.HeartRateEmergencyRepository;
import com.widyu.heart.repository.HeartRateEventRepository;
import com.widyu.heart.repository.HeartRateResultRepository;
import com.widyu.member.repository.MemberRepository;
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
    public HeartGraphPageResponse getHeartGraph(Long memberId) {
        HeartRateResult latest = heartRateResultRepository.findByMemberId(memberId).orElse(null);

        List<HeartRateEvent> allEvents = heartRateEventRepository.findByMemberIdOrderByMeasuredAtAsc(memberId);
        Integer maxHeartRate = heartRateEventRepository.findMaxHeartRateByMemberId(memberId).orElse(null);
        Integer minHeartRate = heartRateEventRepository.findMinHeartRateByMemberId(memberId).orElse(null);

        HeartRateEvent latestEvent = getLatestEvent(allEvents);
        HeartGraphCurrentResponse current = buildCurrentForInitial(latest, latestEvent, maxHeartRate, minHeartRate);

        HeartRateEmergency firstEmergency = heartRateEmergencyRepository.findFirstByMemberIdOrderByMeasuredAtAsc(memberId).orElse(null);
        HeartRateEventResponse firstEmergencyResponse = null;
        if (firstEmergency != null) {
            firstEmergencyResponse = HeartRateEventResponse.from(firstEmergency);
        }

        List<HeartRateEventResponse> eventResponses = allEvents.stream()
                .map(HeartRateEventResponse::from)
                .toList();

        HeartGraphResponse heartGraph = HeartGraphResponse.forInitial(current, firstEmergencyResponse, eventResponses);

        return HeartGraphPageResponse.of(heartGraph, buildEmergencyHistory(memberId));
    }

    @Transactional(readOnly = true)
    public HeartGraphPageResponse getHeartGraphRefresh(Long memberId) {
        HeartRateResult latest = heartRateResultRepository.findByMemberId(memberId).orElse(null);

        Integer maxHeartRate = heartRateEventRepository.findMaxHeartRateByMemberId(memberId).orElse(null);
        Integer minHeartRate = heartRateEventRepository.findMinHeartRateByMemberId(memberId).orElse(null);

        List<HeartRateEvent> recentHeartRateEvents = heartRateEventRepository.findTop5ByMemberIdOrderByMeasuredAtDesc(memberId);
        HeartRateEvent latestEvent = recentHeartRateEvents.stream()
                .max(Comparator.comparing(HeartRateEvent::getMeasuredAt))
                .orElse(null);
        HeartGraphCurrentResponse current = buildCurrentForRefresh(latest, latestEvent, maxHeartRate, minHeartRate);

        List<HeartRateEventResponse> recentEvents = recentHeartRateEvents
                .stream()
                .sorted(Comparator.comparing(HeartRateEvent::getMeasuredAt))
                .map(HeartRateEventResponse::from)
                .toList();

        HeartGraphResponse heartGraph = HeartGraphResponse.forRefresh(current, recentEvents);

        return HeartGraphPageResponse.of(heartGraph, buildEmergencyHistory(memberId));
    }

    private HeartGraphCurrentResponse buildCurrentForInitial(
            HeartRateResult latest,
            HeartRateEvent latestEvent,
            Integer maxHeartRate,
            Integer minHeartRate
    ) {
        if (latest == null) {
            return buildCurrentForInitialFromEvent(latestEvent, maxHeartRate, minHeartRate);
        }
        return HeartGraphCurrentResponse.forInitial(
                latest.getHeartRate(), latest.getMeasuredAt(), maxHeartRate, minHeartRate, latest.getStatus());
    }

    private HeartGraphCurrentResponse buildCurrentForInitialFromEvent(
            HeartRateEvent latestEvent,
            Integer maxHeartRate,
            Integer minHeartRate
    ) {
        if (latestEvent == null) {
            return HeartGraphCurrentResponse.forInitial(null, null, maxHeartRate, minHeartRate, HeartRateStatus.UNKNOWN);
        }
        return HeartGraphCurrentResponse.forInitial(
                latestEvent.getHeartRate(),
                latestEvent.getMeasuredAt(),
                maxHeartRate,
                minHeartRate,
                latestEvent.getStatus()
        );
    }

    private HeartGraphCurrentResponse buildCurrentForRefresh(
            HeartRateResult latest,
            HeartRateEvent latestEvent,
            Integer maxHeartRate,
            Integer minHeartRate
    ) {
        if (latest == null) {
            return buildCurrentForRefreshFromEvent(latestEvent, maxHeartRate, minHeartRate);
        }
        return HeartGraphCurrentResponse.forRefresh(latest.getHeartRate(), maxHeartRate, minHeartRate, latest.getStatus());
    }

    private HeartGraphCurrentResponse buildCurrentForRefreshFromEvent(
            HeartRateEvent latestEvent,
            Integer maxHeartRate,
            Integer minHeartRate
    ) {
        if (latestEvent == null) {
            return HeartGraphCurrentResponse.forRefresh(null, maxHeartRate, minHeartRate, HeartRateStatus.UNKNOWN);
        }
        return HeartGraphCurrentResponse.forRefresh(
                latestEvent.getHeartRate(),
                maxHeartRate,
                minHeartRate,
                latestEvent.getStatus()
        );
    }

    private HeartRateEvent getLatestEvent(List<HeartRateEvent> events) {
        return events.stream()
                .max(Comparator.comparing(HeartRateEvent::getMeasuredAt))
                .orElse(null);
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
