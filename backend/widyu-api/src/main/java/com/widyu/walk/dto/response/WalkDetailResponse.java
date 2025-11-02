package com.widyu.walk.dto.response;

import com.widyu.walk.Walk;

public record WalkDetailResponse(
        Integer goal,
        Integer actual,
        Integer point
) {
    public static WalkDetailResponse from(Walk walk) {
        return new WalkDetailResponse(
                walk.getGoalSteps(),
                walk.getActualSteps(),
                walk.getPointRewarded()
        );
    }

    public static WalkDetailResponse withDefault(Integer defaultGoal) {
        return new WalkDetailResponse(defaultGoal, 0, 0);
    }
}
