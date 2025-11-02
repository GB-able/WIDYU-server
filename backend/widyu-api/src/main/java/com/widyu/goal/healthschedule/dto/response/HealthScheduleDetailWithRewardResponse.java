package com.widyu.goal.healthschedule.dto.response;

import com.widyu.healthschedule.HealthSchedule;
import com.widyu.healthschedule.ProgressStatus;
import java.time.LocalDateTime;

public record HealthScheduleDetailWithRewardResponse(
        Long healthScheduleId,
        LocalDateTime scheduledAt,
        String locationName,
        ProgressStatus progressStatus,
        Integer rewardPoint,
        Boolean isReward
) {
    public static HealthScheduleDetailWithRewardResponse from(HealthSchedule healthSchedule) {
        return new HealthScheduleDetailWithRewardResponse(
                healthSchedule.getId(),
                healthSchedule.getScheduledAt(),
                healthSchedule.getPlaceAddress(),
                healthSchedule.getProgressStatus(),
                healthSchedule.getRewardPoint(),
                healthSchedule.getIsReward()
        );
    }
}
