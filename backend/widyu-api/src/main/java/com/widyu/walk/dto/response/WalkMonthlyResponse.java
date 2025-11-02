package com.widyu.walk.dto.response;

import java.util.List;

public record WalkMonthlyResponse(
        WalkSummary summary,
        List<WalkDaily> dailyData
) {
    public record WalkSummary(
            MonthStats previous,
            MonthStats current
    ) {
        public record MonthStats(
                long achieved,
                long total
        ) {
        }
    }

    public record WalkDaily(
            String date,
            Integer goal,
            Integer actual
    ) {
    }
}
