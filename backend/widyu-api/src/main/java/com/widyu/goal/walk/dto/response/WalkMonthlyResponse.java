package com.widyu.goal.walk.dto.response;

import com.widyu.goal.rate.GoalRateCalculator;
import com.widyu.goal.rate.GoalTally;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.YearMonth;
import java.util.List;

public record WalkMonthlyResponse(
        @Schema(description = "폐기 예정. 요청 달의 전달(previous)과 요청 달(current)의 달성 일수. comparison을 쓴다.",
                deprecated = true)
        WalkSummary summary,
        List<WalkDaily> dailyData,
        @Schema(description = "상단 두 달 비교 박스. 오른쪽은 항상 이번 달이고, 보는 달이 이번 달이면 왼쪽은 지난달, 아니면 왼쪽은 보는 달이다.")
        MonthComparison comparison
) {
    public static WalkMonthlyResponse of(WalkSummary summary, List<WalkDaily> dailyData, MonthComparison comparison) {
        return new WalkMonthlyResponse(summary, dailyData, comparison);
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

    public record MonthComparison(
            @Schema(description = "왼쪽 박스(지난달 또는 보는 달)")
            MonthProgress left,
            @Schema(description = "오른쪽 박스(이번 달)")
            MonthProgress right
    ) {
        public static MonthComparison of(MonthProgress left, MonthProgress right) {
            return new MonthComparison(left, right);
        }
    }

    public record MonthProgress(
            @Schema(example = "2026") int year,
            @Schema(example = "11") int month,
            @Schema(description = "걸은 걸음 합계. 하루 걸음은 그날 목표까지만 센다", example = "100000") long achieved,
            @Schema(description = "목표 걸음 합계. 이번 달은 1일~오늘, 지난 달은 말일까지, 이번 달 이후는 0", example = "150000")
            long total,
            @Schema(description = "achieved ÷ total 정수 퍼센트(반올림). total이 0이면 null", example = "67", nullable = true)
            Integer rate,
            @Schema(description = "목표를 달성한 날 수", example = "15") long achievedDays,
            @Schema(description = "목표가 있던 날 수(같은 기간)", example = "30") long totalDays
    ) {
        public static MonthProgress of(YearMonth month, GoalTally steps, GoalTally days) {
            return new MonthProgress(month.getYear(), month.getMonthValue(), steps.done(), steps.target(),
                    GoalRateCalculator.percent(steps), days.done(), days.target());
        }
    }
}
