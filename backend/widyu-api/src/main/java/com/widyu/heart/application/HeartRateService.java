package com.widyu.heart.application;

import com.widyu.heart.HeartRateResult;
import com.widyu.heart.HeartRateStatus;
import com.widyu.heart.dto.request.HeartRateMeasurement;
import com.widyu.heart.dto.request.HeartRateSendRequest;
import com.widyu.heart.dto.response.HeartRateStatusResponse;
import com.widyu.heart.repository.HeartRateResultRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class HeartRateService {

    private final HeartRateAnomalyDetector heartRateAnomalyDetector;
    private final HeartRateResultRepository heartRateResultRepository;

    public HeartRateStatusResponse processHeartRates(Long memberId, HeartRateSendRequest request) {
        List<Integer> heartRateValues = request.heartRates().stream()
                .map(HeartRateMeasurement::heartRate)
                .toList();

        boolean isAbnormal = heartRateAnomalyDetector.detectAnomaly(heartRateValues);

        HeartRateStatus status = isAbnormal ? HeartRateStatus.ANOMALY : HeartRateStatus.NORMAL;

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

        log.info("심박수 분석 완료: memberId={}, status={}, heartRate={}, measuredAt={}",
                memberId, status, latestMeasurement.heartRate(), latestMeasurement.measuredAt());

        return HeartRateStatusResponse.from(result);
    }

    public HeartRateStatusResponse getHeartRateStatus(Long memberId) {
        return heartRateResultRepository.findByMemberId(memberId)
                .map(HeartRateStatusResponse::from)
                .orElse(HeartRateStatusResponse.unknown(memberId));
    }
}
