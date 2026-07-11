package com.widyu.heart.application;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
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
import com.widyu.member.Member;
import com.widyu.member.repository.MemberRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HeartRateService {

    private final HeartRateAnomalyDetector heartRateAnomalyDetector;
    private final HeartRateResultRepository heartRateResultRepository;
    private final HeartRateEventRepository heartRateEventRepository;
    private final HeartRateEmergencyRepository heartRateEmergencyRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public HeartRateStatusResponse processHeartRates(Long memberId, HeartRateSendRequest request) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        List<Integer> heartRateValues = request.heartRates().stream()
                .map(HeartRateMeasurement::heartRate)
                .toList();

        boolean isAbnormal = heartRateAnomalyDetector.detectAnomaly(heartRateValues);

        HeartRateStatus status = resolveStatus(isAbnormal);

        HeartRateMeasurement latestMeasurement = request.heartRates().stream()
                .max(Comparator.comparing(HeartRateMeasurement::measuredAt))
                .orElse(request.heartRates().getLast());

        HeartRateResult result = HeartRateResult.of(
                memberId,
                status,
                latestMeasurement.heartRate(),
                latestMeasurement.measuredAt()
        );
        heartRateResultRepository.save(result);

        List<HeartRateEvent> events = request.heartRates().stream()
                .map(m -> HeartRateEvent.of(member, m.heartRate(), m.measuredAt(), status))
                .toList();
        heartRateEventRepository.saveAll(events);

        if (isAbnormal) {
            Integer peakHeartRate = heartRateValues.stream().max(Integer::compareTo).orElse(latestMeasurement.heartRate());
            HeartRateEmergency emergency = HeartRateEmergency.of(member, peakHeartRate, latestMeasurement.measuredAt(), request.location());
            heartRateEmergencyRepository.save(emergency);
        }

        log.info("심박수 분석 완료: memberId={}, status={}, heartRate={}, measuredAt={}",
                memberId, status, latestMeasurement.heartRate(), latestMeasurement.measuredAt());

        return HeartRateStatusResponse.from(result);
    }

    public HeartRateStatusResponse getHeartRateStatus(Long memberId) {
        return heartRateResultRepository.findByMemberId(memberId)
                .map(HeartRateStatusResponse::from)
                .orElse(HeartRateStatusResponse.unknown(memberId));
    }

    @Transactional(readOnly = true)
    public HeartGraphPageResponse getHeartGraph(Long memberId) {
        HeartRateResult latest = heartRateResultRepository.findByMemberId(memberId).orElse(null);

        List<HeartRateEvent> allEvents = heartRateEventRepository.findByMemberIdOrderByMeasuredAtAsc(memberId);
        Integer maxHeartRate = heartRateEventRepository.findMaxHeartRateByMemberId(memberId).orElse(null);
        Integer minHeartRate = heartRateEventRepository.findMinHeartRateByMemberId(memberId).orElse(null);

        HeartGraphCurrentResponse current = buildCurrentForInitial(latest, maxHeartRate, minHeartRate);

        HeartRateEmergency firstEmergency = heartRateEmergencyRepository.findFirstByMemberIdOrderByMeasuredAtAsc(memberId).orElse(null);
        HeartRateEventResponse firstEmergencyResponse = null;
        if (firstEmergency != null) {
            firstEmergencyResponse = new HeartRateEventResponse(firstEmergency.getHeartRate(), firstEmergency.getMeasuredAt());
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

        HeartGraphCurrentResponse current = buildCurrentForRefresh(latest, maxHeartRate, minHeartRate);

        List<HeartRateEventResponse> recentEvents = heartRateEventRepository
                .findTop5ByMemberIdOrderByMeasuredAtDesc(memberId)
                .stream()
                .sorted(Comparator.comparing(HeartRateEvent::getMeasuredAt))
                .map(HeartRateEventResponse::from)
                .toList();

        HeartGraphResponse heartGraph = HeartGraphResponse.forRefresh(current, recentEvents);

        return HeartGraphPageResponse.of(heartGraph, buildEmergencyHistory(memberId));
    }

    private HeartGraphCurrentResponse buildCurrentForInitial(HeartRateResult latest, Integer maxHeartRate, Integer minHeartRate) {
        if (latest == null) {
            return HeartGraphCurrentResponse.forInitial(null, null, maxHeartRate, minHeartRate, HeartRateStatus.UNKNOWN);
        }
        return HeartGraphCurrentResponse.forInitial(
                latest.getHeartRate(), latest.getMeasuredAt(), maxHeartRate, minHeartRate, latest.getStatus());
    }

    private HeartGraphCurrentResponse buildCurrentForRefresh(HeartRateResult latest, Integer maxHeartRate, Integer minHeartRate) {
        if (latest == null) {
            return HeartGraphCurrentResponse.forRefresh(null, maxHeartRate, minHeartRate, HeartRateStatus.UNKNOWN);
        }
        return HeartGraphCurrentResponse.forRefresh(latest.getHeartRate(), maxHeartRate, minHeartRate, latest.getStatus());
    }

    private HeartRateStatus resolveStatus(boolean isAbnormal) {
        if (isAbnormal) {
            return HeartRateStatus.ANOMALY;
        }
        return HeartRateStatus.NORMAL;
    }

    private EmergencyHistoryResponse buildEmergencyHistory(Long memberId) {
        long emergencyCount = heartRateEmergencyRepository.countByMemberId(memberId);
        int totalDuration = heartRateEventRepository.findTotalDurationMinutesByMemberId(memberId).orElse(0);
        List<EmergencyEventResponse> emergencyEvents = heartRateEmergencyRepository
                .findByMemberIdOrderByMeasuredAtDesc(memberId)
                .stream()
                .map(EmergencyEventResponse::from)
                .toList();
        return new EmergencyHistoryResponse(emergencyCount, totalDuration, emergencyEvents);
    }
}
