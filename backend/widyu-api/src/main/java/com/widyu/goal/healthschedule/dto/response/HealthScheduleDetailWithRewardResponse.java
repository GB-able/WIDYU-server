package com.widyu.goal.healthschedule.dto.response;

import com.widyu.healthschedule.HealthSchedule;
import com.widyu.healthschedule.ProgressStatus;
import java.time.LocalDateTime;

public record HealthScheduleDetailWithRewardResponse(
        Long healthScheduleId,
        String scheduleName,
        LocalDateTime scheduledAt,
        String placeAddress,
        Double latitude,
        Double longitude,
        ProgressStatus progressStatus,
        Integer rewardPoint,
        Boolean isReward
) {
    public static HealthScheduleDetailWithRewardResponse from(HealthSchedule healthSchedule) {
        return new HealthScheduleDetailWithRewardResponse(
                healthSchedule.getId(),
                healthSchedule.getScheduleName(),
                healthSchedule.getScheduledAt(),
                healthSchedule.getPlaceAddress(),
                healthSchedule.getLatitude(),
                healthSchedule.getLongitude(),
                healthSchedule.getProgressStatus(),
                healthSchedule.getRewardPoint(),
                healthSchedule.getIsReward()
        );
    }
}
