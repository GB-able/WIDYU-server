package com.widyu.goal.walk.dto.response;

import java.util.List;

public record WalkMonthlyResponse(
        WalkSummary summary,
        List<WalkDaily> dailyData
) {
    public static WalkMonthlyResponse of(WalkSummary summary, List<WalkDaily> dailyData) {
        return new WalkMonthlyResponse(summary, dailyData);
    }

    public record WalkSummary(
            MonthStats previous,
            MonthStats current
    ) {
        public static WalkSummary of(MonthStats previous, MonthStats current) {
            return new WalkSummary(previous, current);
        }

        public record MonthStats(
                long achieved,
                long total
        ) {
            public static MonthStats of(long achieved, long total) {
                return new MonthStats(achieved, total);
            }
        }
    }

    public record WalkDaily(
            String date,
            Integer goal,
            Integer actual
    ) {
        public static WalkDaily of(String date, Integer goal, Integer actual) {
            return new WalkDaily(date, goal, actual);
        }
    }
}
