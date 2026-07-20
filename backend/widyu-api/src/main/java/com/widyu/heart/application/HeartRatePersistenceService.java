package com.widyu.heart.application;

import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.heart.HeartRateEmergency;
import com.widyu.heart.HeartRateEvent;
import com.widyu.heart.HeartRateResult;
import com.widyu.heart.HeartRateStatus;
import com.widyu.heart.dto.request.HeartRateMeasurement;
import com.widyu.heart.dto.request.HeartRateSendRequest;
import com.widyu.heart.repository.HeartRateEmergencyRepository;
import com.widyu.heart.repository.HeartRateEventRepository;
import com.widyu.heart.repository.HeartRateResultRepository;
import com.widyu.member.Member;
import com.widyu.member.repository.MemberRepository;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HeartRatePersistenceService {

    private final HeartRateResultRepository heartRateResultRepository;
    private final HeartRateEventRepository heartRateEventRepository;
    private final HeartRateEmergencyRepository heartRateEmergencyRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public HeartRateResult saveAnalysis(
            Long memberId,
            HeartRateSendRequest request,
            HeartRateStatus status,
            boolean isAbnormal
    ) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

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
            saveEmergency(member, request, latestMeasurement);
        }

        return result;
    }

    private void saveEmergency(Member member, HeartRateSendRequest request, HeartRateMeasurement latestMeasurement) {
        Integer peakHeartRate = request.heartRates().stream()
                .map(HeartRateMeasurement::heartRate)
                .max(Integer::compareTo)
                .orElse(latestMeasurement.heartRate());
        HeartRateEmergency emergency = HeartRateEmergency.of(
                member,
                peakHeartRate,
                latestMeasurement.measuredAt(),
                request.location()
        );
        heartRateEmergencyRepository.save(emergency);
    }
}
