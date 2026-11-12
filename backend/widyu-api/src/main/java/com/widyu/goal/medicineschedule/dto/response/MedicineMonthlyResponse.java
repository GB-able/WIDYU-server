package com.widyu.goal.medicineschedule.dto.response;

import com.widyu.goal.rate.GoalRateCalculator;
import com.widyu.goal.rate.GoalTally;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.YearMonth;
import java.util.List;

public record MedicineMonthlyResponse(
        @Schema(description = "폐기 예정. 요청 달의 전달에 그날 약을 모두 먹은 날 수. comparison을 쓴다.",
                example = "25", deprecated = true)
        Integer lastMonthAchieveCount,

        @Schema(description = "폐기 예정. 요청 달에 그날 약을 모두 먹은 날 수. comparison을 쓴다.",
                example = "18", deprecated = true)
        Integer currentMonthAchieveCount,

        @Schema(description = "요청 달 1일~말일의 일별 달성률(복용 ÷ 예정, 0.0~1.0). 정수 퍼센트로 반올림한 뒤 100으로 나눈 값이며 예정이 없는 날은 0.0")
        List<Double> monthlyGoalRates,

        @Schema(description = "상단 두 달 비교 박스. 오른쪽은 항상 이번 달이고, 보는 달이 이번 달이면 왼쪽은 지난달, 아니면 왼쪽은 보는 달이다.")
        MonthComparison comparison
) {
    public static MedicineMonthlyResponse of(
            Integer lastMonthAchieveCount,
            Integer currentMonthAchieveCount,
            List<Double> monthlyGoalRates,
            MonthComparison comparison
    ) {
        return new MedicineMonthlyResponse(lastMonthAchieveCount, currentMonthAchieveCount, monthlyGoalRates,
                comparison);
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
            @Schema(description = "복용한 횟수", example = "20") long achieved,
            @Schema(description = "예정 복용 횟수. 이번 달은 1일~오늘, 지난 달은 말일까지, 이번 달 이후는 0", example = "30")
            long total,
            @Schema(description = "achieved ÷ total 정수 퍼센트(반올림). total이 0이면 null", example = "67", nullable = true)
            Integer rate
    ) {
        public static MonthProgress of(YearMonth month, GoalTally doses) {
            return new MonthProgress(month.getYear(), month.getMonthValue(), doses.done(), doses.target(),
                    GoalRateCalculator.percent(doses));
        }
    }
}
