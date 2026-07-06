package com.widyu.goal.medicineschedule.dto.response;

import java.util.List;

public record MedicineMonthlyResponse(
        Integer lastMonthAchieveCount,
        Integer currentMonthAchieveCount,
        List<Double> monthlyGoalRates
) {
    public static MedicineMonthlyResponse of(
            Integer lastMonthAchieveCount,
            Integer currentMonthAchieveCount,
            List<Double> monthlyGoalRates
    ) {
        return new MedicineMonthlyResponse(lastMonthAchieveCount, currentMonthAchieveCount, monthlyGoalRates);
    }
}
