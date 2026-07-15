package com.widyu.heart.dto.response;

import com.widyu.heart.HeartRateEvent;
import com.widyu.heart.HeartRateResult;
import com.widyu.heart.HeartRateStatus;
import java.time.LocalDateTime;

public record HeartRateStatusResponse(
        Long memberId,
        HeartRateStatus heartRateStatus,
        Integer heartRate,
        LocalDateTime measuredAt
) {
    public static HeartRateStatusResponse from(HeartRateResult result) {
        return new HeartRateStatusResponse(
                result.getMemberId(),
                result.getStatus(),
                result.getHeartRate(),
                result.getMeasuredAt()
        );
    }

    public static HeartRateStatusResponse from(Long memberId, HeartRateEvent event) {
        return new HeartRateStatusResponse(
                memberId,
                event.getStatus(),
                event.getHeartRate(),
                event.getMeasuredAt()
        );
    }

    public static HeartRateStatusResponse unknown(Long memberId) {
        return new HeartRateStatusResponse(
                memberId,
                HeartRateStatus.UNKNOWN,
                null,
                null
        );
    }
}
