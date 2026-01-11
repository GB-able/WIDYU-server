package com.widyu.goal.walk.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "내일부터 적용될 목표 걸음수 응답")
public record UpcomingGoalResponse(
        @Schema(description = "내일부터 적용될 목표 걸음수", example = "10000")
        Integer steps
) {
    public static UpcomingGoalResponse of(Integer steps) {
        return new UpcomingGoalResponse(steps);
    }
}
