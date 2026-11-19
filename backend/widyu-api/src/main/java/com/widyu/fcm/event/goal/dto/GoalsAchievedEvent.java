package com.widyu.fcm.event.goal.dto;

import java.util.Collections;
import java.util.List;

/**
 * 한 번의 처리에서 목표 2개 이상 달성(G02). 지금은 위치 수신 한 번에 건강 일정 여러 개가 함께 완료되는 경로만 있다.
 * 같은 일정은 한 번만 보상되므로 「가장 작은 일정 ID + 개수」로 묶음을 고정할 수 있다.
 */
public record GoalsAchievedEvent(Long seniorId, int count, long totalPoints, String eventId) {

    public static GoalsAchievedEvent forHealthSchedules(Long seniorId, List<Long> scheduleIds, long totalPoints) {
        return new GoalsAchievedEvent(seniorId, scheduleIds.size(), totalPoints,
                "G02:H:" + Collections.min(scheduleIds) + ":" + scheduleIds.size());
    }
}
