package com.widyu.fcm.event.goal.dto;

import com.widyu.fcm.dto.GuardianDeepLinks;

/** 목표 하나 달성(G01). 보호자 딥링크는 달성한 목표의 위듀케어 화면이다(위듀케어 정책서 v18 G01-C). */
public record GoalAchievedEvent(Long seniorId, String goalName, long points, String entityId, String eventId,
                                String guardianDeepLink) {

    public static GoalAchievedEvent forWalk(Long seniorId, Long walkId, long points) {
        return new GoalAchievedEvent(seniorId, "걷기", points, walkId.toString(), "G01:W:" + walkId,
                GuardianDeepLinks.walkGoal(seniorId));
    }

    public static GoalAchievedEvent forHealthSchedule(Long seniorId, Long scheduleId, long points) {
        return new GoalAchievedEvent(seniorId, "건강 일정", points, scheduleId.toString(), "G01:H:" + scheduleId,
                GuardianDeepLinks.healthScheduleGoal(seniorId));
    }

    public static GoalAchievedEvent forMedicationDay(Long seniorId, Long proofId, long points) {
        return new GoalAchievedEvent(seniorId, "하루 복약", points, proofId.toString(), "G01:M:" + proofId,
                GuardianDeepLinks.medicineGoal(seniorId));
    }
}
