package com.widyu.goal.home.dto.response;

import com.widyu.goal.DailyGoalStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record SeniorWeeklyGoalStatusResponse(
        @Schema(description = "이번 주 목표 달성 상태 (일~토)",
                example = "[\"NOT_STARTED\", \"IN_PROGRESS\", \"COMPLETED\", \"FAILED\"]")
        List<DailyGoalStatus> thisWeekGoalRates
) {
}
